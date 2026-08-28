package kr.givemeticket.api.apply.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 10, 0);

    @Test
    @DisplayName("처음 큐에 들어가는 이벤트는 재시도 횟수가 0이다")
    void first() {
        ReservationEvent event = ReservationEvent.first(100L, 1L, 7L, NOW);

        assertThat(event.applicationId()).isEqualTo(100L);
        assertThat(event.campaignId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(7L);
        assertThat(event.attempt()).isZero();
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("식별자가 비면 큐에 넣기 전에 막는다")
    void rejectsMissingIds() {
        assertThatThrownBy(() -> new ReservationEvent(null, 1L, 7L, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationEvent(100L, null, 7L, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationEvent(100L, 1L, null, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("선점 시각이 없으면 큐에서 머문 시간을 잴 수 없다")
    void rejectsMissingOccurredAt() {
        assertThatThrownBy(() -> new ReservationEvent(100L, 1L, 7L, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재시도 횟수는 음수일 수 없다")
    void rejectsNegativeAttempt() {
        assertThatThrownBy(() -> new ReservationEvent(100L, 1L, 7L, -1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nextAttempt 는 원본을 두고 재시도 횟수만 올린 사본을 만든다")
    void nextAttempt() {
        ReservationEvent first = ReservationEvent.first(100L, 1L, 7L, NOW);

        ReservationEvent second = first.nextAttempt();

        assertThat(first.attempt()).isZero();
        assertThat(second.attempt()).isEqualTo(1);
        assertThat(second.applicationId()).isEqualTo(first.applicationId());
        // 큐에서 머문 시간을 재는 기준점이라 재시도해도 움직이면 안 된다.
        assertThat(second.occurredAt()).isEqualTo(first.occurredAt());
    }
}
