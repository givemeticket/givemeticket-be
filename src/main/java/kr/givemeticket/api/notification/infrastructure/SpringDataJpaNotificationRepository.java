package kr.givemeticket.api.notification.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.notification.domain.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SpringDataJpaNotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByIdDesc(Long userId, Limit limit);

    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Limit limit);

    /**
     * 안쪽에서 LIMIT 으로 끊고 바깥에서 센다. 안 읽은 알림이 수천 건이어도 {@code cap} 건만 읽는다.
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT 1 FROM notification
                 WHERE user_id = :userId
                   AND read_at IS NULL
                 LIMIT :cap
            ) unread
            """, nativeQuery = true)
    int countUnread(@Param("userId") Long userId, @Param("cap") int cap);

    /** {@code @Modifying} 은 JPA 감사를 타지 않으므로 updatedAt 을 직접 넣는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
               SET n.readAt = :now,
                   n.updatedAt = :now
             WHERE n.userId = :userId
               AND n.readAt IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /**
     * 한 번에 지우는 양을 끊는다. 수십만 건을 한 문장으로 지우면 그동안 잠금과 undo 가 쌓인다.
     * 정리 스케줄러가 트랜잭션 밖에서 부르므로 배치마다 따로 커밋한다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM notification WHERE created_at < :before LIMIT :limit",
            nativeQuery = true)
    int deleteCreatedBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
