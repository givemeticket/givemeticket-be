package kr.givemeticket.api.apply.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataJpaApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByCampaignIdAndUserId(Long campaignId, Long userId);

    List<Application> findAllByUserIdAndStatusInOrderByIdDesc(
            Long userId, Collection<ApplicationStatus> statuses);

    long countByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses);

    List<Application> findAllByCampaignIdAndStatusIn(
            Long campaignId, Collection<ApplicationStatus> statuses);

    /*
     * 아래 둘은 조건부 UPDATE 다. 반환된 행 수가 1일 때만 재고를 건드리기 때문에,
     * 같은 신청에 취소가 동시에 들어와도 재고 복원은 정확히 한 번만 일어난다.
     * @Modifying 은 JPA 감사(auditing)를 타지 않으므로 updatedAt 을 직접 넣는다.
     */

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Application a
               SET a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.CANCELLED,
                   a.updatedAt = :now
             WHERE a.id = :id
               AND a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.CONFIRMED
            """)
    int cancelIfConfirmed(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 캠페인 삭제나 회원 탈퇴처럼 사용자가 직접 누르지 않은 일괄 취소.
     * 왜 취소됐는지는 reason 으로 남긴다.
     *
     * <p>그 사이 사용자가 직접 취소했으면 0행이 바뀐다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Application a
               SET a.status = kr.givemeticket.api.apply.domain.ApplicationStatus.CANCELLED,
                   a.failureReason = :reason,
                   a.updatedAt = :now
             WHERE a.id = :id
               AND a.status IN :statuses
            """)
    int cancelWithReason(
            @Param("id") Long id,
            @Param("statuses") Collection<ApplicationStatus> statuses,
            @Param("reason") FailureReason reason,
            @Param("now") LocalDateTime now);
}
