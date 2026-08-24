package kr.givemeticket.api.apply.application;

import java.util.List;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.application.CampaignApplicationException;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationPersister applicationPersister;
    private final CampaignRepository campaignRepository;
    private final CampaignStateRepository campaignStateRepository;
    private final StockRepository stockRepository;

    /**
     * 자리를 잡으면 그대로 확정이다. 결제 단계가 없어 PENDING 을 거치지 않는다.
     */
    public ApplicationResponse apply(Long campaignId, Long userId) {
        CampaignState state = campaignStateRepository.find(campaignId)
                .orElseThrow(CampaignApplicationException::notOpen);

        decreaseStock(campaignId);

        try {
            return ApplicationResponse.from(applicationPersister.confirm(campaignId, userId));
        } catch (RuntimeException e) {
            stockRepository.restore(campaignId, state.totalStock());
            throw translatePersistFailure(campaignId, e);
        }
    }

    public ApplicationResponse cancel(Long applicationId, Long userId) {
        Application application = findOwnedApplication(applicationId, userId);

        if (application.getStatus() != ApplicationStatus.CONFIRMED) {
            throw ApplyApplicationException.notCancelable(application.getStatus());
        }

        Campaign campaign = campaignOf(application);

        if (applicationPersister.cancel(applicationId) == 0) {
            throw ApplyApplicationException.notCancelable(currentStatusOf(applicationId));
        }

        stockRepository.restore(application.getCampaignId(), campaign.getTotalStock());
        log.info("application cancelled: applicationId={}, campaignId={}",
                applicationId, application.getCampaignId());

        return currentStateOf(applicationId);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(Long applicationId, Long userId) {
        return ApplicationResponse.from(findOwnedApplication(applicationId, userId));
    }

    /**
     * 캠페인이 삭제되어 남은 신청을 일괄 취소한다.
     * 재고는 캠페인과 함께 사라지므로 복원하지 않는다.
     *
     * <p>호출자가 신규 신청을 먼저 막은 뒤 불러야 한다. 그러지 않으면 취소하는 사이에 들어온
     * 신청이 대상에서 누락된다.
     *
     * @return 실제로 취소된 신청 수
     */
    public int cancelAllByCampaignDeletion(Campaign campaign) {
        List<Application> targets = applicationRepository
                .findAllByCampaignIdAndStatusIn(campaign.getId(), ApplicationStatus.active());

        int cancelled = 0;
        for (Application application : targets) {
            // 그 사이 사용자가 직접 취소했으면 0행이다.
            if (applicationPersister.cancelByCampaignDeletion(application.getId()) == 0) {
                continue;
            }
            cancelled++;
        }

        if (cancelled > 0) {
            log.info("applications cancelled by campaign deletion: campaignId={}, count={}",
                    campaign.getId(), cancelled);
        }
        return cancelled;
    }

    /**
     * 회원 탈퇴로 본인의 남은 신청을 일괄 취소한다.
     *
     * <p>캠페인 삭제와 달리 행사는 그대로 살아 있다. 비운 자리는 다른 사람이 쓸 수 있어야 하므로
     * 재고를 반드시 되돌린다.
     *
     * @return 실제로 취소된 신청 수
     */
    public int cancelAllByUserWithdrawal(Long userId) {
        List<Application> targets = applicationRepository
                .findAllByUserIdAndStatusIn(userId, ApplicationStatus.active());

        int cancelled = 0;
        for (Application application : targets) {
            Campaign campaign = campaignRepository.findById(application.getCampaignId()).orElse(null);
            if (campaign == null) {
                continue;
            }

            // 그 사이 사용자가 직접 취소했으면 0행이다.
            if (applicationPersister.cancelByUserWithdrawal(application.getId()) == 0) {
                continue;
            }
            cancelled++;

            stockRepository.restore(application.getCampaignId(), campaign.getTotalStock());
        }

        if (cancelled > 0) {
            log.info("applications cancelled by user withdrawal: userId={}, count={}", userId, cancelled);
        }
        return cancelled;
    }

    private void decreaseStock(Long campaignId) {
        StockDecreaseResult result = stockRepository.decrease(campaignId);
        switch (result.status()) {
            case NOT_INITIALIZED -> throw CampaignApplicationException.stockNotInitialized();
            case SOLD_OUT -> throw CampaignApplicationException.soldOut();
            case SUCCESS -> { }
        }
    }

    private Application findOwnedApplication(Long applicationId, Long userId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(ApplyApplicationException::applicationNotFound);
        if (!application.isOwnedBy(userId)) {
            throw ApplyApplicationException.forbidden();
        }
        return application;
    }

    private Campaign campaignOf(Application application) {
        return campaignRepository.findById(application.getCampaignId())
                .orElseThrow(CampaignApplicationException::campaignNotFound);
    }

    private ApplicationStatus currentStatusOf(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .map(Application::getStatus)
                .orElse(ApplicationStatus.CANCELLED);
    }

    private ApplicationResponse currentStateOf(Long applicationId) {
        return ApplicationResponse.from(applicationRepository.findById(applicationId)
                .orElseThrow(ApplyApplicationException::applicationNotFound));
    }

    private RuntimeException translatePersistFailure(Long campaignId, RuntimeException e) {
        if (e instanceof DataIntegrityViolationException) {
            log.info("duplicate apply rejected by unique constraint: campaignId={}", campaignId);
            return ApplyApplicationException.alreadyApplied();
        }
        return e;
    }
}
