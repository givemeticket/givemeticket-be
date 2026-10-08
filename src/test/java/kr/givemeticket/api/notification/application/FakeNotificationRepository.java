package kr.givemeticket.api.notification.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * id 를 차례로 붙이고 {@code (user_id, dedupe_key)} 유니크 제약을 흉내낸다.
 */
class FakeNotificationRepository implements NotificationRepository {

    final List<Notification> rows = new ArrayList<>();
    private long sequence = 0;

    @Override
    public Notification save(Notification notification) {
        if (notification.getDedupeKey() != null && rows.stream().anyMatch(row ->
                row.getUserId().equals(notification.getUserId())
                        && Objects.equals(row.getDedupeKey(), notification.getDedupeKey()))) {
            throw new DataIntegrityViolationException("uk_notification_user_dedupe");
        }
        TestEntities.with(notification, "id", ++sequence);
        TestEntities.with(notification, "createdAt", LocalDateTime.now());
        rows.add(notification);
        return notification;
    }

    @Override
    public void saveAll(Collection<Notification> notifications) {
        notifications.forEach(this::save);
    }

    @Override
    public Optional<Notification> findById(Long notificationId) {
        return rows.stream().filter(row -> row.getId().equals(notificationId)).findFirst();
    }

    @Override
    public List<Notification> findPage(Long userId, Long cursor, int limit) {
        return rows.stream()
                .filter(row -> row.getUserId().equals(userId))
                .filter(row -> cursor == null || row.getId() < cursor)
                .sorted(Comparator.comparing(Notification::getId).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public int countUnread(Long userId, int cap) {
        return (int) rows.stream()
                .filter(row -> row.getUserId().equals(userId) && !row.isRead())
                .limit(cap)
                .count();
    }

    @Override
    public int markAllRead(Long userId, LocalDateTime now) {
        List<Notification> unread = rows.stream()
                .filter(row -> row.getUserId().equals(userId) && !row.isRead())
                .toList();
        unread.forEach(row -> row.markRead(now));
        return unread.size();
    }

    @Override
    public int deleteAllByUserId(Long userId) {
        int before = rows.size();
        rows.removeIf(row -> row.getUserId().equals(userId));
        return before - rows.size();
    }

    @Override
    public int deleteCreatedBefore(LocalDateTime before, int limit) {
        throw new UnsupportedOperationException();
    }

    List<Notification> of(Long userId) {
        return rows.stream().filter(row -> row.getUserId().equals(userId)).toList();
    }
}
