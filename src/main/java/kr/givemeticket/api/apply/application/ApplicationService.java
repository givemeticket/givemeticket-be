package kr.givemeticket.api.apply.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationIdIssuer;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.PendingReservation;
import kr.givemeticket.api.apply.domain.PendingReservationStore;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import kr.givemeticket.api.apply.domain.ReservationQueue;
import kr.givemeticket.api.campaign.application.CampaignApplicationException;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ApplicationIdIssuer applicationIdIssuer;
    private final ReservationQueue reservationQueue;
    private final PendingReservationStore pendingReservationStore;

    /**
     * 자리를 잡고 큐에 넣는다. 결제 단계가 없어 곧바로 확정이다.
     *
     * <p><b>MySQL 저장은 여기서 일어나지 않는다.</b> 행을 만드는 것은 워커다.
     * 사용자가 보는 응답은 달라지지 않는다.
     */
    public ApplicationResponse apply(Long campaignId, Long userId) {
        CampaignState state = campaignStateRepository.find(campaignId)
                .orElseThrow(CampaignApplicationException::notOpen);

        reserveSeat(campaignId, userId);

        try {
            Long applicationId = applicationIdFor(campaignId, userId);

            // 큐에 넣기 전에 대기 레코드를 둔다. 순서가 반대면 워커가 먼저 집어간 뒤
            // 조회가 들어왔을 때 DB 에도 Redis 에도 없는 찰나가 생긴다.
            pendingReservationStore.put(new PendingReservation(applicationId, campaignId, userId));
            reservationQueue.publish(
                    ReservationEvent.first(applicationId, campaignId, userId, LocalDateTime.now()));

            return ApplicationResponse.accepted(applicationId, campaignId, userId);
        } catch (RuntimeException e) {
            stockRepository.restore(campaignId, userId, state.totalStock());
            throw e;
        }
    }

    /**
     * 이 예매가 쓸 번호를 정한다. 취소됐던 행이 있으면 그 번호를 그대로 쓰고,
     * 없으면 새로 채번한다.
     *
     * <p>요청 경로에 남은 유일한 DB 접근이다. 자리를 잡는 데 성공한 요청만 오므로
     * 횟수는 정원으로 상한이 잡힌다.
     */
    private Long applicationIdFor(Long campaignId, Long userId) {
        return applicationRepository.findByCampaignIdAndUserId(campaignId, userId)
                .map(Application::getId)
                .orElseGet(applicationIdIssuer::issue);
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

        stockRepository.restore(
                application.getCampaignId(), application.getUserId(), campaign.getTotalStock());
        log.info("application cancelled: applicationId={}, campaignId={}",
                applicationId, application.getCampaignId());

        return currentStateOf(applicationId);
    }

    /** 신청을 조회한다. 워커가 아직 행을 만들기 전이면 대기 레코드가 답한다. */
    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(Long applicationId, Long userId) {
        Application application = applicationRepository.findById(applicationId).orElse(null);
        if (application != null) {
            if (!application.isOwnedBy(userId)) {
                throw ApplyApplicationException.forbidden();
            }
            return ApplicationResponse.from(application);
        }

        PendingReservation pending = pendingReservationStore.find(applicationId)
                .orElseThrow(ApplyApplicationException::applicationNotFound);
        if (!pending.userId().equals(userId)) {
            throw ApplyApplicationException.forbidden();
        }
        return ApplicationResponse.accepted(
                pending.applicationId(), pending.campaignId(), pending.userId());
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

            stockRepository.restore(
                application.getCampaignId(), application.getUserId(), campaign.getTotalStock());
        }

        if (cancelled > 0) {
            log.info("applications cancelled by user withdrawal: userId={}, count={}", userId, cancelled);
        }
        return cancelled;
    }

    /** 자리 하나를 잡는다. 중복 확인과 재고 차감이 Redis 안에서 한 번에 끝난다. */
    private void reserveSeat(Long campaignId, Long userId) {
        StockDecreaseResult result = stockRepository.decrease(campaignId, userId);
        switch (result.status()) {
            case NOT_INITIALIZED -> throw CampaignApplicationException.stockNotInitialized();
            case ALREADY_APPLIED -> throw ApplyApplicationException.alreadyApplied();
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

}
