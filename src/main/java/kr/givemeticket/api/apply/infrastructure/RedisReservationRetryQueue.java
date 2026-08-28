package kr.givemeticket.api.apply.infrastructure;

import java.time.Duration;
import java.util.List;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import kr.givemeticket.api.apply.domain.ReservationRetryQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * 지연 재시도 큐의 Redis ZSET 구현.
 *
 * <p>언제: 워커가 저장에 실패했을 때 넣고, 스케줄러가 1초마다 꺼낸다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisReservationRetryQueue implements ReservationRetryQueue {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> reservationRetryPromoteScript;
    private final ReservationQueueProperties properties;

    @Value("${reservation.retry.promote-batch-size:100}")
    private int promoteBatchSize;

    /**
     * score 에 다음 재시도 시각을 넣는다. 정렬 기준이 곧 스케줄이라
     * {@code ZRANGE ... WITHSCORES} 로 상태를 그대로 들여다볼 수 있다.
     */
    @Override
    public void schedule(ReservationEvent event, Duration delay) {
        long dueAt = System.currentTimeMillis() + delay.toMillis();
        stringRedisTemplate.opsForZSet().add(
                properties.delayQueueKey(), ReservationEventCodec.flatten(event), dueAt);

        log.info("reservation retry scheduled: applicationId={}, attempt={}, delayMs={}",
                event.applicationId(), event.attempt(), delay.toMillis());
    }

    @Override
    public long promoteDue() {
        Long moved = stringRedisTemplate.execute(
                reservationRetryPromoteScript,
                List.of(properties.delayQueueKey(), properties.streamKey()),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(promoteBatchSize),
                String.valueOf(properties.maxLength()));
        return (moved == null) ? 0L : moved;
    }
}
