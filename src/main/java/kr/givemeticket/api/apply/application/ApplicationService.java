package kr.givemeticket.api.apply.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.application.dto.response.CancelResponse;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.application.CampaignApplicationException;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
import kr.givemeticket.api.payment.domain.PaymentClient;
import kr.givemeticket.api.payment.domain.PaymentException;
import kr.givemeticket.api.payment.domain.PaymentResult;
import kr.givemeticket.api.payment.domain.RefundStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationPersister applicationPersister;
    private final CampaignRepository campaignRepository;
    private final CampaignStateRepository campaignStateRepository;
    private final StockRepository stockRepository;
    private final PaymentClient paymentClient;
    private final Duration holdDuration;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationPersister applicationPersister,
            CampaignRepository campaignRepository,
            CampaignStateRepository campaignStateRepository,
            StockRepository stockRepository,
            PaymentClient paymentClient,
            @Value("${application.hold-duration:2m}") Duration holdDuration
    ) {
        this.applicationRepository = applicationRepository;
        this.applicationPersister = applicationPersister;
        this.campaignRepository = campaignRepository;
        this.campaignStateRepository = campaignStateRepository;
        this.stockRepository = stockRepository;
        this.paymentClient = paymentClient;
        this.holdDuration = holdDuration;
    }

    public ApplicationResponse apply(Long campaignId, Long userId) {
        CampaignState state = campaignStateRepository.find(campaignId)
                .orElseThrow(CampaignApplicationException::notOpen);

        decreaseStock(campaignId);

        try {
            return ApplicationResponse.from(state.requiresPayment()
                    ? applicationPersister.reserve(campaignId, userId,
                            UUID.randomUUID().toString(), LocalDateTime.now().plus(holdDuration))
                    : applicationPersister.confirmImmediately(campaignId, userId));
        } catch (RuntimeException e) {
            stockRepository.restore(campaignId, state.totalStock());
            throw translatePersistFailure(campaignId, e);
        }
    }

    public ApplicationResponse confirm(Long applicationId, Long userId) {
        Application application = findOwnedApplication(applicationId, userId);
        if (!application.isPending()) {
            throw ApplyApplicationException.notPending(application.getStatus());
        }

        int upperBound = campaignOf(application).getTotalStock();

        if (application.isExpired(LocalDateTime.now())) {
            expire(application, upperBound);
            throw ApplyApplicationException.expired();
        }

        applicationPersister.markPaymentRequested(applicationId);
        PaymentResult payment = paymentClient.charge(
                application.getPaymentKey(), applicationId, userId);

        return switch (payment.outcome()) {
            case APPROVED -> settleApproved(application, payment);
            case DECLINED -> {
                failAndRestore(application, upperBound, FailureReason.PAYMENT_DECLINED);
                throw ApplyApplicationException.paymentDeclined();
            }
            case ERROR -> {
                failAndRestore(application, upperBound, FailureReason.PAYMENT_ERROR);
                throw PaymentException.gatewayError();
            }
            case UNKNOWN -> settleUnknown(application);
        };
    }

    public CancelResponse cancel(Long applicationId, Long userId) {
        Application application = findOwnedApplication(applicationId, userId);

        if (application.getStatus() == ApplicationStatus.UNKNOWN
                || application.getStatus() == ApplicationStatus.MANUAL_REVIEW) {
            throw ApplyApplicationException.cancelPendingSettlement();
        }
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

        return new CancelResponse(currentStateOf(applicationId), refund(application, campaign));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(Long applicationId, Long userId) {
        return ApplicationResponse.from(findOwnedApplication(applicationId, userId));
    }

    private void decreaseStock(Long campaignId) {
        StockDecreaseResult result = stockRepository.decrease(campaignId);
        switch (result.status()) {
            case NOT_INITIALIZED -> throw CampaignApplicationException.stockNotInitialized();
            case SOLD_OUT -> throw CampaignApplicationException.soldOut();
            case SUCCESS -> { }
        }
    }

    private ApplicationResponse settleApproved(Application application, PaymentResult payment) {
        if (applicationPersister.confirm(application.getId(), payment.transactionId()) == 0) {
            log.error("payment approved but application was no longer pending: "
                            + "applicationId={}, paymentKey={}, transactionId={}",
                    application.getId(), application.getPaymentKey(), payment.transactionId());
        }
        return currentStateOf(application.getId());
    }

    private ApplicationResponse settleUnknown(Application application) {
        applicationPersister.markUnknown(application.getId());
        log.error("payment outcome unknown, stock held for reconciliation: applicationId={}, paymentKey={}",
                application.getId(), application.getPaymentKey());
        return currentStateOf(application.getId());
    }

    private void expire(Application application, int upperBound) {
        failAndRestore(application, upperBound, FailureReason.EXPIRED);
    }

    private void failAndRestore(Application application, int upperBound, FailureReason reason) {
        if (applicationPersister.fail(application.getId(), reason) == 0) {
            log.warn("stock not restored, application already settled: applicationId={}, reason={}",
                    application.getId(), reason);
            return;
        }
        stockRepository.restore(application.getCampaignId(), upperBound);
        log.info("stock restored: applicationId={}, campaignId={}, reason={}",
                application.getId(), application.getCampaignId(), reason);
    }

    private RefundStatus refund(Application application, Campaign campaign) {
        if (!campaign.isRequiresPayment() || application.getPaymentKey() == null) {
            return RefundStatus.NOT_REQUIRED;
        }
        if (paymentClient.cancel(application.getPaymentKey())) {
            return RefundStatus.COMPLETED;
        }
        log.error("refund request failed, retry needed: applicationId={}, paymentKey={}",
                application.getId(), application.getPaymentKey());
        return RefundStatus.PENDING_RETRY;
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
