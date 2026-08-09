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

/**
 * 신청 한 번으로 재고 확보부터 결제 확정까지 끝낸다.
 *
 * <p>이 클래스에는 {@code @Transactional}이 없다. 결제 HTTP 호출을 트랜잭션 밖에 두기 위해서다.
 * 상태를 바꾸는 구간은 {@link ApplicationPersister}의 짧은 트랜잭션으로 분리돼 있다.
 */
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
            @Value("${payment.hold-duration:60s}") Duration holdDuration
    ) {
        this.applicationRepository = applicationRepository;
        this.applicationPersister = applicationPersister;
        this.campaignRepository = campaignRepository;
        this.campaignStateRepository = campaignStateRepository;
        this.stockRepository = stockRepository;
        this.paymentClient = paymentClient;
        this.holdDuration = holdDuration;
    }

    /**
     * 결과는 셋 중 하나로 끝난다.
     * <ul>
     *   <li>CONFIRMED — 확정</li>
     *   <li>UNKNOWN — 결제 결과 불명. 재고를 잡은 채 정산을 기다린다. 클라이언트는 폴링한다</li>
     *   <li>예외 — 매진·중복·거절·게이트웨이 오류</li>
     * </ul>
     */
    public ApplicationResponse apply(Long campaignId, Long userId) {
        // 오픈 여부·결제 필요 여부·정원을 Redis 한 번으로 읽는다.
        CampaignState state = campaignStateRepository.find(campaignId)
                .orElseThrow(CampaignApplicationException::notOpen);

        // 중복 확인보다 재고 차감을 먼저 한다. 오픈 직후에는 요청 대부분이 매진으로 떨어지는데,
        // 중복 확인이 앞에 있으면 그 요청들이 전부 DB를 한 번씩 긁고 나간다.
        // 이 순서면 매진 경로는 Redis 두 번으로 끝나고 커넥션을 아예 잡지 않는다.
        decreaseStock(campaignId);

        Application existing;
        try {
            existing = applicationRepository
                    .findByCampaignIdAndUserId(campaignId, userId)
                    .orElse(null);
        } catch (RuntimeException e) {
            stockRepository.restore(campaignId, state.totalStock());
            throw e;
        }

        if (existing != null && existing.isActive()) {
            // 이 사용자는 이미 자리를 갖고 있다. 방금 잡은 자리는 그대로 돌려준다.
            stockRepository.restore(campaignId, state.totalStock());

            if (state.requiresPayment() && existing.isPending()) {
                // 앞선 요청이 결제 도중 끊겼거나 사용자가 새로고침한 경우다.
                // 그 행이 이미 자리를 붙들고 있으므로 멱등키를 유지한 채 결제만 다시 시도한다.
                return resumePayment(existing, state);
            }
            throw ApplyApplicationException.alreadyApplied();
        }

        if (!state.requiresPayment()) {
            return confirmWithoutPayment(campaignId, userId, state);
        }
        return reserveAndCharge(campaignId, userId, state);
    }

    /**
     * 결제가 없던 신청은 외부 호출 없이 그 자리에서 끝난다.
     * 결제가 있었으면 자리를 먼저 돌려주고 환불을 요청한다 — 환불이 실패해도 취소는 유지된다.
     */
    public CancelResponse cancel(Long applicationId, Long userId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(ApplyApplicationException::applicationNotFound);
        if (!application.isOwnedBy(userId)) {
            throw ApplyApplicationException.forbidden();
        }
        if (application.getStatus() == ApplicationStatus.UNKNOWN
                || application.getStatus() == ApplicationStatus.MANUAL_REVIEW) {
            throw ApplyApplicationException.cancelPendingSettlement();
        }
        if (application.getStatus() != ApplicationStatus.CONFIRMED) {
            throw ApplyApplicationException.notCancelable(application.getStatus());
        }

        Campaign campaign = campaignRepository.findById(application.getCampaignId())
                .orElseThrow(CampaignApplicationException::campaignNotFound);

        if (applicationPersister.cancel(applicationId) == 0) {
            // 동시에 들어온 다른 취소가 먼저 통과했다. 재고를 또 돌려주면 정원을 넘는다.
            throw ApplyApplicationException.notCancelable(
                    currentStatusOf(applicationId));
        }

        stockRepository.restore(application.getCampaignId(), campaign.getTotalStock());
        log.info("application cancelled: applicationId={}, campaignId={}",
                applicationId, application.getCampaignId());

        return new CancelResponse(currentStateOf(applicationId), refund(application, campaign));
    }

    private RefundStatus refund(Application application, Campaign campaign) {
        if (!campaign.isRequiresPayment() || application.getPaymentKey() == null) {
            return RefundStatus.NOT_REQUIRED;
        }
        if (paymentClient.cancel(application.getPaymentKey())) {
            return RefundStatus.COMPLETED;
        }
        // 취소는 이미 확정됐다. 되돌리지 않고 남겨서 나중에 다시 시도한다.
        // 자동 재시도 큐는 아직 없으므로 이 로그가 유일한 추적 수단이다.
        log.error("refund request failed, retry needed: applicationId={}, paymentKey={}",
                application.getId(), application.getPaymentKey());
        return RefundStatus.PENDING_RETRY;
    }

    private ApplicationStatus currentStatusOf(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .map(Application::getStatus)
                .orElse(ApplicationStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(Long applicationId, Long userId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(ApplyApplicationException::applicationNotFound);
        if (!application.isOwnedBy(userId)) {
            throw ApplyApplicationException.forbidden();
        }
        return ApplicationResponse.from(application);
    }

    private void decreaseStock(Long campaignId) {
        StockDecreaseResult result = stockRepository.decrease(campaignId);
        switch (result.status()) {
            case NOT_INITIALIZED -> throw CampaignApplicationException.stockNotInitialized();
            case SOLD_OUT -> throw CampaignApplicationException.soldOut();
            case SUCCESS -> { }
        }
    }

    private ApplicationResponse confirmWithoutPayment(Long campaignId, Long userId, CampaignState state) {
        try {
            return ApplicationResponse.from(
                    applicationPersister.confirmImmediately(campaignId, userId));
        } catch (RuntimeException e) {
            stockRepository.restore(campaignId, state.totalStock());
            throw translatePersistFailure(campaignId, e);
        }
    }

    private ApplicationResponse reserveAndCharge(Long campaignId, Long userId, CampaignState state) {
        Application application;
        try {
            application = applicationPersister.reserve(
                    campaignId, userId, UUID.randomUUID().toString(),
                    LocalDateTime.now().plus(holdDuration));
        } catch (RuntimeException e) {
            stockRepository.restore(campaignId, state.totalStock());
            throw translatePersistFailure(campaignId, e);
        }
        return charge(application, state);
    }

    private ApplicationResponse resumePayment(Application existing, CampaignState state) {
        Application application = applicationPersister.extendHold(
                existing.getId(), LocalDateTime.now().plus(holdDuration));
        return charge(application, state);
    }

    private ApplicationResponse charge(Application application, CampaignState state) {
        Long applicationId = application.getId();
        String paymentKey = application.getPaymentKey();

        // 호출 직전에 흔적을 남긴다. 여기서 서버가 죽어도 "보냈는지 여부"를 나중에 판단할 수 있다.
        applicationPersister.markPaymentRequested(applicationId);

        PaymentResult payment = paymentClient.charge(paymentKey, applicationId, application.getUserId());

        return switch (payment.outcome()) {
            case APPROVED -> settleApproved(application, payment);
            case DECLINED -> {
                failAndRestore(application, state, FailureReason.PAYMENT_DECLINED);
                throw ApplyApplicationException.paymentDeclined();
            }
            case ERROR -> {
                failAndRestore(application, state, FailureReason.PAYMENT_ERROR);
                throw PaymentException.gatewayError();
            }
            case UNKNOWN -> settleUnknown(application);
        };
    }

    private ApplicationResponse settleApproved(Application application, PaymentResult payment) {
        if (applicationPersister.confirm(application.getId(), payment.transactionId()) == 0) {
            // 결제는 승인됐는데 그 사이 다른 주체가 상태를 바꿔놨다. 돈이 나간 건이므로 조용히 넘기지 않는다.
            log.error("payment approved but application was no longer pending: "
                            + "applicationId={}, paymentKey={}, transactionId={}",
                    application.getId(), application.getPaymentKey(), payment.transactionId());
        }
        return currentStateOf(application.getId());
    }

    /**
     * 재고를 되돌리지 않는다. 승인됐을 가능성이 남아 있는 자리를 남에게 팔면
     * 돈은 빠져나갔는데 티켓은 없는 상태가 된다. 정산 배치가 PG에 다시 물어볼 때까지 잡아둔다.
     */
    private ApplicationResponse settleUnknown(Application application) {
        applicationPersister.markUnknown(application.getId());
        log.error("payment outcome unknown, stock held for reconciliation: applicationId={}, paymentKey={}",
                application.getId(), application.getPaymentKey());
        return currentStateOf(application.getId());
    }

    private void failAndRestore(Application application, CampaignState state, FailureReason reason) {
        if (applicationPersister.fail(application.getId(), reason) == 0) {
            // 만료 sweeper 등이 이미 전이시켰다. 그쪽이 복원했으므로 여기서 또 되돌리면 재고가 부풀어 오른다.
            log.warn("stock not restored, application already settled: applicationId={}, reason={}",
                    application.getId(), reason);
            return;
        }
        stockRepository.restore(application.getCampaignId(), state.totalStock());
        log.info("stock restored: applicationId={}, campaignId={}, reason={}",
                application.getId(), application.getCampaignId(), reason);
    }

    private ApplicationResponse currentStateOf(Long applicationId) {
        return ApplicationResponse.from(applicationRepository.findById(applicationId)
                .orElseThrow(ApplyApplicationException::applicationNotFound));
    }

    private RuntimeException translatePersistFailure(Long campaignId, RuntimeException e) {
        if (e instanceof DataIntegrityViolationException) {
            // 유니크 제약 위반 = 같은 사용자의 다른 요청이 간발의 차로 먼저 신청을 만들었다.
            log.info("duplicate apply rejected by unique constraint: campaignId={}", campaignId);
            return ApplyApplicationException.alreadyApplied();
        }
        log.warn("apply persist failed, stock restored: campaignId={}, cause={}", campaignId, e.toString());
        return e;
    }
}
