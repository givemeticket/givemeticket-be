package kr.givemeticket.api.notification.infrastructure;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SpringDataJpaNotificationOutboxRepository
        extends JpaRepository<NotificationOutbox, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    /**
     * SKIP LOCKED 로 다른 워커가 잡은 행을 건너뛴다. 파드가 여럿이어도 같은 원본을 동시에
     * 펼치지 않고, 서로 기다리지도 않는다. (MySQL 8.0 이상)
     */
    @Query(value = """
            SELECT * FROM notification_outbox
             WHERE processed_at IS NULL
             ORDER BY id
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificationOutbox> claimNext();

    /** 정리 스케줄러가 트랜잭션 밖에서 부른다. 배치마다 따로 커밋한다. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM notification_outbox WHERE processed_at < :before LIMIT :limit",
            nativeQuery = true)
    int deleteProcessedBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
