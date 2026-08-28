package kr.givemeticket.api.apply.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(
            Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(1), 5);

    @Test
    @DisplayName("대기 시간이 시도마다 배로 늘어난다")
    void doublesEachAttempt() {
        // jitter 가 0~1초라 구간으로 본다. 1s / 2s / 4s / 8s 에 난수가 얹힌다.
        assertThat(policy.nextDelay(0)).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(2));
        assertThat(policy.nextDelay(1)).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(3));
        assertThat(policy.nextDelay(2)).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(5));
        assertThat(policy.nextDelay(3)).isBetween(Duration.ofSeconds(8), Duration.ofSeconds(9));
    }

    @Test
    @DisplayName("아무리 늘어도 상한을 넘지 않는다 — 상한이 없으면 금방 하루가 된다")
    void capsAtMaxDelay() {
        assertThat(policy.nextDelay(20))
                .isBetween(Duration.ofSeconds(30), Duration.ofSeconds(31));
    }

    @Test
    @DisplayName("같은 시도 번호라도 매번 다른 값이 나온다 — 함께 돌아오지 않게")
    void spreadsWithJitter() {
        long distinct = IntStream.range(0, 50)
                .mapToLong(i -> policy.nextDelay(3).toMillis())
                .distinct()
                .count();

        assertThat(distinct).isGreaterThan(1);
    }

    @Test
    @DisplayName("최대 횟수를 채우면 더는 재시도하지 않는다")
    void exhaustsAtMaxAttempts() {
        assertThat(policy.isExhausted(0)).isFalse();
        assertThat(policy.isExhausted(3)).isFalse();
        // 4번째 시도(attempt=4)가 실패하면 다음은 5번째라 한도를 넘는다.
        assertThat(policy.isExhausted(4)).isTrue();
        assertThat(policy.isExhausted(9)).isTrue();
    }

    @Test
    @DisplayName("jitter 를 끄면 정확히 배수만 남는다")
    void worksWithoutJitter() {
        RetryPolicy noJitter = new RetryPolicy(
                Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ZERO, 5);

        assertThat(noJitter.nextDelay(2)).isEqualTo(Duration.ofSeconds(4));
    }
}
