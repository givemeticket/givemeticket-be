package kr.givemeticket.api.apply.application;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.FailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청의 트랜잭션 경계를 모아둔다.
 *
 * <p>{@link ApplicationService}는 트랜잭션을 열지 않는다. 결제 HTTP 호출이 트랜잭션 안에 있으면
 * 결제가 느려질 때마다 DB 커넥션이 그만큼 묶여서, 결제 지연이 곧 커넥션 풀 고갈이 되기 때문이다.
 * 그래서 상태를 건드리는 구간만 이 클래스의 짧은 트랜잭션으로 쪼갠다.
 *
 * <p>전이 메서드들이 int를 반환하는 것도 의도적이다. 반환값이 1일 때만 호출자가 재고를 되돌리므로,
 * 만료 sweeper와 요청 스레드가 같은 신청을 동시에 처리해도 복원은 정확히 한 번만 일어난다.
 * 또 이 메서드가 반환된 시점엔 트랜잭션이 이미 커밋됐기 때문에, 롤백된 전이에 대해
 * 재고만 늘어나는 일도 없다.
 */
@Service
@RequiredArgsConstructor
public class ApplicationPersister {

    private final ApplicationRepository applicationRepository;

    /**
     * 결제 대기 상태로 자리를 잡는다. 종결된 이전 신청이 있으면 그 행을 재사용한다.
     */
    @Transactional
    public Application reserve(Long campaignId, Long userId, String paymentKey, LocalDateTime expiresAt) {
        Application application = applicationRepository.findByCampaignIdAndUserId(campaignId, userId)
                .map(existing -> {
                    existing.reserve(paymentKey, expiresAt);
                    return existing;
                })
                .orElseGet(() -> Application.pending(campaignId, userId, paymentKey, expiresAt));

        return applicationRepository.save(application);
    }

    /**
     * 결제가 필요 없는 캠페인. 중간 상태 없이 바로 확정한다.
     */
    @Transactional
    public Application confirmImmediately(Long campaignId, Long userId) {
        Application application = applicationRepository.findByCampaignIdAndUserId(campaignId, userId)
                .map(existing -> {
                    existing.reserveConfirmed();
                    return existing;
                })
                .orElseGet(() -> Application.confirmed(campaignId, userId));

        return applicationRepository.save(application);
    }

    /**
     * 이미 PENDING인 신청의 결제를 다시 시도한다. 재고는 그 행이 이미 잡고 있으므로 건드리지 않고,
     * 멱등키도 그대로 둔다 — 앞선 시도가 실제로 승인됐다면 PG가 같은 결과를 돌려줘야 하기 때문이다.
     */
    @Transactional
    public Application extendHold(Long applicationId, LocalDateTime expiresAt) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(ApplyApplicationException::applicationNotFound);
        application.reserve(application.getPaymentKey(), expiresAt);
        return application;
    }

    @Transactional
    public void markPaymentRequested(Long applicationId) {
        applicationRepository.findById(applicationId)
                .ifPresent(application -> application.markPaymentRequested(LocalDateTime.now()));
    }

    @Transactional
    public int confirm(Long applicationId, String transactionId) {
        return applicationRepository.confirmIfPending(applicationId, transactionId);
    }

    @Transactional
    public int fail(Long applicationId, FailureReason reason) {
        return applicationRepository.failIfPending(applicationId, reason);
    }

    @Transactional
    public int markUnknown(Long applicationId) {
        return applicationRepository.markUnknownIfPending(applicationId);
    }

    @Transactional
    public int cancel(Long applicationId) {
        return applicationRepository.cancelIfConfirmed(applicationId);
    }
}
