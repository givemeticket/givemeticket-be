package kr.givemeticket.api.apply.application;

import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.campaign.application.CampaignApplicationException;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
import kr.givemeticket.api.payment.domain.PaymentClient;
import kr.givemeticket.api.payment.domain.PaymentResult;
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
    private final CampaignStateRepository campaignStateRepository;
    private final StockRepository stockRepository;
    private final PaymentClient paymentClient;

    public ApplicationResponse apply(Long campaignId, Long userId) {
        ensureOpen(campaignId);

        StockDecreaseResult result = stockRepository.decrease(campaignId);
        switch (result.status()) {
            case NOT_INITIALIZED -> throw CampaignApplicationException.stockNotInitialized();
            case SOLD_OUT -> throw CampaignApplicationException.soldOut();
            case SUCCESS -> { }
        }

        try {
            Application application = applicationPersister.persist(campaignId, userId);
            return ApplicationResponse.from(application);
        } catch (RuntimeException e) {
            stockRepository.increase(campaignId);
            log.warn("apply persist failed, stock restored: campaignId={}, cause={}", campaignId, e.toString());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(Long applicationId, Long userId) {
        Application application = findOwnedApplication(applicationId, userId);
        return ApplicationResponse.from(application);
    }

    @Transactional
    public ApplicationResponse confirm(Long applicationId, Long userId) {
        Application application = findOwnedApplication(applicationId, userId);

        if (!application.isPending()) {
            throw ApplyApplicationException.notPending();
        }

        PaymentResult payment = paymentClient.charge(applicationId, userId);

        if (payment.approved()) {
            application.confirm(payment.transactionId());
        } else {
            application.fail();
            stockRepository.increase(application.getCampaignId());
            log.info("payment declined, stock restored: applicationId={}", applicationId);
        }

        return ApplicationResponse.from(application);
    }

    private void ensureOpen(Long campaignId) {
        if (!campaignStateRepository.isOpen(campaignId)) {
            throw CampaignApplicationException.notOpen();
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
}
