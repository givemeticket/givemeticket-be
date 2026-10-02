package kr.givemeticket.api.notification.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaNotificationRepository implements NotificationRepository {

    private final SpringDataJpaNotificationRepository springDataJpaNotificationRepository;

    @Override
    public Notification save(Notification notification) {
        return springDataJpaNotificationRepository.save(notification);
    }

    /**
     * IDENTITY 키라 Hibernate 가 INSERT 를 묶어 보내지 못하고 한 건씩 나간다. 수신자 수는 정원이
     * 상한이고 요청 경로가 아닌 워커에서만 부르므로 그대로 둔다.
     */
    @Override
    public void saveAll(Collection<Notification> notifications) {
        springDataJpaNotificationRepository.saveAll(notifications);
    }

    @Override
    public Optional<Notification> findById(Long notificationId) {
        return springDataJpaNotificationRepository.findById(notificationId);
    }

    /**
     * 정렬 기준이 created_at 이 아니라 id 다. 한 번에 펼친 알림은 생성 시각이 같아도
     * id 로는 순서가 갈리고, 커서도 id 하나로 끝난다.
     */
    @Override
    public List<Notification> findPage(Long userId, Long cursor, int limit) {
        if (cursor == null) {
            return springDataJpaNotificationRepository.findByUserIdOrderByIdDesc(
                    userId, Limit.of(limit));
        }
        return springDataJpaNotificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(
                userId, cursor, Limit.of(limit));
    }

    @Override
    public int countUnread(Long userId, int cap) {
        return springDataJpaNotificationRepository.countUnread(userId, cap);
    }

    @Override
    public int markAllRead(Long userId, LocalDateTime now) {
        return springDataJpaNotificationRepository.markAllRead(userId, now);
    }

    @Override
    public int deleteAllByUserId(Long userId) {
        return springDataJpaNotificationRepository.deleteAllByUserId(userId);
    }

    @Override
    public int deleteCreatedBefore(LocalDateTime before, int limit) {
        return springDataJpaNotificationRepository.deleteCreatedBefore(before, limit);
    }
}
