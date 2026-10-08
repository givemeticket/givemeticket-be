package kr.givemeticket.api.notification.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 잠금은 흉내내지 않는다. "처리 전인 것 중 가장 오래된 하나"를 돌려주는 규칙과
 * dedupe_key 유니크 제약만 맞춘다.
 */
class FakeNotificationOutboxRepository implements NotificationOutboxRepository {

    final List<NotificationOutbox> rows = new ArrayList<>();
    private long sequence = 0;

    @Override
    public NotificationOutbox save(NotificationOutbox outbox) {
        if (outbox.getDedupeKey() != null && existsByDedupeKey(outbox.getDedupeKey())) {
            throw new DataIntegrityViolationException("uk_notification_outbox_dedupe");
        }
        TestEntities.with(outbox, "id", ++sequence);
        rows.add(outbox);
        return outbox;
    }

    @Override
    public boolean existsByDedupeKey(String dedupeKey) {
        return rows.stream().anyMatch(row -> Objects.equals(row.getDedupeKey(), dedupeKey));
    }

    @Override
    public Optional<NotificationOutbox> claimNext() {
        return rows.stream().filter(row -> row.getProcessedAt() == null).findFirst();
    }

    @Override
    public int deleteProcessedBefore(LocalDateTime before, int limit) {
        throw new UnsupportedOperationException();
    }
}
