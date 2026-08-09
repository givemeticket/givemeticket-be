package kr.givemeticket.api.apply.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataJpaApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByCampaignIdAndUserId(Long campaignId, Long userId);

    List<Application> findAllByUserIdAndStatusInOrderByIdDesc(
            Long userId, Collection<ApplicationStatus> statuses);

    @Query("""
            SELECT a FROM Application a
             WHERE a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.PENDING
               AND a.expiresAt < :now
             ORDER BY a.expiresAt ASC
            """)
    List<Application> findExpiredPending(@Param("now") LocalDateTime now, Pageable pageable);

    long countByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses);

    boolean existsByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses);

    /*
     * 아래 세 개는 모두 "PENDING일 때만" 전이한다. 반환된 행 수가 1일 때만 재고를 건드리기 때문에,
     * sweeper와 요청 스레드가 같은 신청을 동시에 처리해도 재고 복원은 정확히 한 번만 일어난다.
     * @Modifying 은 JPA 감사(auditing)를 타지 않으므로 updatedAt 을 직접 넣는다.
     */

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Application a
               SET a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.CONFIRMED,
                   a.transactionId = :transactionId,
                   a.expiresAt = null,
                   a.updatedAt = :now
             WHERE a.id = :id
               AND a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.PENDING
            """)
    int confirmIfPending(
            @Param("id") Long id,
            @Param("transactionId") String transactionId,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Application a
               SET a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.FAILED,
                   a.failureReason = :reason,
                   a.expiresAt = null,
                   a.updatedAt = :now
             WHERE a.id = :id
               AND a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.PENDING
            """)
    int failIfPending(
            @Param("id") Long id,
            @Param("reason") FailureReason reason,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Application a
               SET a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.UNKNOWN,
                   a.updatedAt = :now
             WHERE a.id = :id
               AND a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.PENDING
            """)
    int markUnknownIfPending(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 확정된 신청만 취소한다. 동시에 두 번 눌러도 한 번만 통과하므로 재고도 한 번만 돌아간다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Application a
               SET a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.CANCELLED,
                   a.expiresAt = null,
                   a.updatedAt = :now
             WHERE a.id = :id
               AND a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.CONFIRMED
            """)
    int cancelIfConfirmed(@Param("id") Long id, @Param("now") LocalDateTime now);
}
