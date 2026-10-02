package kr.givemeticket.api.notification.infrastructure;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaNotificationOutboxRepository implements NotificationOutboxRepository {

    private final SpringDataJpaNotificationOutboxRepository springDataJpaNotificationOutboxRepository;

    /**
     * 곧바로 flush 한다. 트랜잭션 밖(임박 알림 스케줄러)에서 부르면 유니크 위반이 여기서 터져야
     * 호출자가 잡을 수 있고, 트랜잭션 안(행사 수정)에서 부르면 실패가 커밋 전에 드러난다.
     */
    @Override
    public NotificationOutbox save(NotificationOutbox outbox) {
        return springDataJpaNotificationOutboxRepository.saveAndFlush(outbox);
    }

    @Override
    public boolean existsByDedupeKey(String dedupeKey) {
        return springDataJpaNotificationOutboxRepository.existsByDedupeKey(dedupeKey);
    }

    @Override
    public Optional<NotificationOutbox> claimNext() {
        return springDataJpaNotificationOutboxRepository.claimNext();
    }

    @Override
    public int deleteProcessedBefore(LocalDateTime before, int limit) {
        return springDataJpaNotificationOutboxRepository.deleteProcessedBefore(before, limit);
    }
}
