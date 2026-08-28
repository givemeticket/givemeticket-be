package kr.givemeticket.api.apply.domain;

import java.time.Duration;

/**
 * 저장에 실패한 메시지를 잠시 재워 두는 지연 큐. 구현은 Redis ZSET 이다.
 *
 * <p>바로 다시 시도하면 회복하려는 DB 를 같은 부하로 다시 때린다. 그 되먹임을 끊는 것이
 * 이 큐의 목적이다.
 *
 * <p>언제: 워커가 저장에 실패했을 때 넣고, 스케줄러가 1초마다 꺼낸다.
 */
public interface ReservationRetryQueue {

    /** {@code delay} 뒤에 메인 큐로 돌아가도록 예약한다. */
    void schedule(ReservationEvent event, Duration delay);

    /** 실행 시각이 된 메시지를 메인 큐로 되돌리고, 되돌린 개수를 반환한다. */
    long promoteDue();
}
