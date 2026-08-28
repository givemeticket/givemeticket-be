package kr.givemeticket.api.apply.application;

import kr.givemeticket.api.apply.domain.ReservationRetryQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지연 큐에서 실행 시각이 된 메시지를 메인 큐로 되돌린다.
 *
 * <p>언제: 1초마다. 인스턴스마다 하나씩 돌지만 꺼내기와 넣기가 원자적이라
 * 같은 메시지가 두 번 들어가지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationRetryScheduler {

    private final ReservationRetryQueue reservationRetryQueue;

    /** 지연 큐를 한 번 훑는다. 실패해도 다음 주기에 다시 시도한다. */
    @Scheduled(fixedDelayString = "${reservation.retry.poll-interval-ms:1000}")
    public void promoteDueRetries() {
        try {
            long moved = reservationRetryQueue.promoteDue();
            if (moved > 0) {
                log.info("reservation retries returned to main queue: count={}", moved);
            }
        } catch (RuntimeException e) {
            log.error("reservation retry promotion failed", e);
        }
    }
}
