package kr.givemeticket.api.apply.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 워커의 쓰기는 반드시 멱등해야 한다. 메시지는 재시도로 두 번 이상 도착하고,
 * 저장 직후 ack 전에 죽는 것도 정상 경로다.
 */
class ApplicationPersisterPersistTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 10, 0);

    private final FakeApplicationRepository repository = new FakeApplicationRepository();
    private final ApplicationPersister persister = new ApplicationPersister(repository);

    @Test
    @DisplayName("행이 없으면 이벤트가 실어 온 id 그대로 만든다")
    void createsWithCarriedId() {
        persister.persist(ReservationEvent.first(101L, 1L, 7L, NOW));

        assertThat(repository.created).singleElement().satisfies(application -> {
            assertThat(application.getId()).isEqualTo(101L);
            assertThat(application.getCampaignId()).isEqualTo(1L);
            assertThat(application.getUserId()).isEqualTo(7L);
            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        });
    }

    @Test
    @DisplayName("같은 메시지가 다시 와도 행이 늘지 않는다")
    void isIdempotentOnRedelivery() {
        ReservationEvent event = ReservationEvent.first(101L, 1L, 7L, NOW);

        persister.persist(event);
        persister.persist(event);
        persister.persist(event.nextAttempt());

        assertThat(repository.rows).hasSize(1);
        assertThat(repository.created).hasSize(1);
    }

    @Test
    @DisplayName("취소됐던 행은 새로 만들지 않고 되살린다")
    void revivesCancelledRow() {
        Application cancelled = Application.confirmed(42L, 1L, 7L, NOW.minusDays(1));
        set(cancelled, "status", ApplicationStatus.CANCELLED);
        set(cancelled, "failureReason", FailureReason.CAMPAIGN_DELETED);
        repository.put(cancelled);

        persister.persist(ReservationEvent.first(42L, 1L, 7L, NOW));

        assertThat(repository.created).isEmpty();
        assertThat(cancelled.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        assertThat(cancelled.getFailureReason()).isNull();
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
