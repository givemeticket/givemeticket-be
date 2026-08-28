package kr.givemeticket.api.apply.application;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 다음 재시도까지 얼마나 재울지 정한다.
 *
 * <p>지수 백오프는 DB 에 회복할 시간을 주고, Jitter 는 메시지들이 같은 순간에
 * 함께 돌아오는 것을 막는다.
 *
 * <p>언제: 워커가 저장에 실패해 지연 큐로 넘길 때.
 */
@Component
public class RetryPolicy {

    private final Duration baseDelay;
    private final Duration maxDelay;
    private final Duration jitter;
    private final int maxAttempts;

    public RetryPolicy(
            @Value("${reservation.retry.base-delay:1s}") Duration baseDelay,
            @Value("${reservation.retry.max-delay:30s}") Duration maxDelay,
            @Value("${reservation.retry.jitter:1s}") Duration jitter,
            @Value("${reservation.retry.max-attempts:5}") int maxAttempts
    ) {
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.jitter = jitter;
        this.maxAttempts = maxAttempts;
    }

    /** 대기 시간을 계산한다. {@code 기본 × 2^attempt + 난수}, 상한 적용. */
    public Duration nextDelay(int attempt) {
        long base = baseDelay.toMillis() << Math.min(attempt, 30);
        long capped = Math.min(base, maxDelay.toMillis());
        long spread = jitter.isZero() ? 0 : ThreadLocalRandom.current().nextLong(jitter.toMillis());
        return Duration.ofMillis(capped + spread);
    }

    /** 방금 실패한 {@code attempt} 로 한도를 채웠는지. 채웠으면 DLQ 로 간다. */
    public boolean isExhausted(int attempt) {
        return attempt + 1 >= maxAttempts;
    }

    /** 설정된 최대 재시도 횟수. 로그와 격리 사유에 적는다. */
    public int maxAttempts() {
        return maxAttempts;
    }
}
