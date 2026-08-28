package kr.givemeticket.api.apply.application;

import java.time.Duration;
import java.util.Map;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import kr.givemeticket.api.apply.domain.ReservationRetryQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 큐에서 꺼낸 예매를 저장하고, 실패하면 지연 큐나 DLQ 로 넘긴다.
 *
 * <p><b>ack 은 성공이 아니라 "메인 큐에서 손을 떼도 된다"는 뜻이다.</b> 저장에
 * 성공했을 때도, 지연 큐나 DLQ 로 옮겨 놓았을 때도 ack 한다. ack 하지 않는 경우는
 * 갈 곳을 마련하지 못했을 때 하나뿐이다.
 *
 * <p>언제: 리스너가 메인 큐에서 메시지를 받을 때마다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationWorker {

    private final ApplicationPersister applicationPersister;
    private final ReservationRetryQueue reservationRetryQueue;
    private final ReservationIsolator reservationIsolator;
    private final RetryPolicy retryPolicy;

    /** 이 메시지를 메인 큐에서 뺄지, 처리 중 목록에 남길지. */
    public enum Disposition {
        ACKNOWLEDGE,
        LEAVE_PENDING
    }

    /** 예매를 저장한다. 실패하면 재시도를 예약하거나 격리한다. */
    public Disposition handle(String messageId, ReservationEvent event) {
        try {
            applicationPersister.persist(event);
            log.debug("reservation persisted: applicationId={}, messageId={}, attempt={}",
                    event.applicationId(), messageId, event.attempt());
            return Disposition.ACKNOWLEDGE;
        } catch (RuntimeException e) {
            return scheduleRetry(messageId, event, e);
        }
    }

    /** 지연 큐로 넘긴다. 한도를 넘겼으면 대신 격리한다. */
    private Disposition scheduleRetry(String messageId, ReservationEvent event, RuntimeException cause) {
        if (retryPolicy.isExhausted(event.attempt())) {
            return isolate(messageId, event,
                    "재시도 %d회 소진: %s".formatted(retryPolicy.maxAttempts(), rootMessage(cause)));
        }

        ReservationEvent next = event.nextAttempt();
        Duration delay = retryPolicy.nextDelay(next.attempt());
        try {
            // 지연 큐에 넣는 것이 먼저다. 넣기 전에 ack 하면 그 사이에 죽었을 때
            // 메시지가 어느 쪽에도 남지 않는다.
            reservationRetryQueue.schedule(next, delay);
        } catch (RuntimeException e) {
            // 재울 곳조차 없다. 여기서 ack 하면 메시지가 사라지므로 처리 중 목록에 남긴다.
            log.error("reservation retry scheduling failed, message left pending: "
                            + "applicationId={}, messageId={}", event.applicationId(), messageId, e);
            return Disposition.LEAVE_PENDING;
        }

        log.warn("reservation persist failed, retry queued: "
                        + "applicationId={}, messageId={}, attempt={} -> {}, delayMs={}",
                event.applicationId(), messageId, event.attempt(), next.attempt(), delay.toMillis(),
                cause);
        return Disposition.ACKNOWLEDGE;
    }

    /** 해석 불가 메시지를 격리한다. 재시도해도 결과가 같아 지연 큐를 건너뛴다. */
    public Disposition handleUndecodable(String messageId, Map<String, String> fields,
                                         RuntimeException cause) {
        try {
            reservationIsolator.isolateRaw(messageId, fields, rootMessage(cause));
            return Disposition.ACKNOWLEDGE;
        } catch (RuntimeException e) {
            log.error("undecodable message isolation failed, left pending: messageId={}",
                    messageId, e);
            return Disposition.LEAVE_PENDING;
        }
    }

    /** DLQ 로 격리한다. 격리 자체가 실패하면 ack 하지 않는다 — 빼면 그대로 유실이다. */
    private Disposition isolate(String messageId, ReservationEvent event, String reason) {
        try {
            reservationIsolator.isolate(event, reason);
            return Disposition.ACKNOWLEDGE;
        } catch (RuntimeException e) {
            log.error("reservation isolation failed, message left pending: "
                            + "applicationId={}, messageId={}", event.applicationId(), messageId, e);
            return Disposition.LEAVE_PENDING;
        }
    }

    /** 격리 사유에 적을 가장 안쪽 원인 한 줄. */
    private String rootMessage(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
