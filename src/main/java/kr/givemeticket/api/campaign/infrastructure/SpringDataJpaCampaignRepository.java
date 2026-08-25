package kr.givemeticket.api.campaign.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataJpaCampaignRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    List<Campaign> findAllByOwnerIdOrderByIdDesc(Long ownerId);

    List<Campaign> findAllByOwnerIdAndStatusNotOrderByIdDesc(Long ownerId, CampaignStatus excluded);

    List<Campaign> findAllByIdInOrderByIdDesc(Collection<Long> ids);

    List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now);

    /**
     * 삭제 표시. 이미 삭제된 캠페인이면 0행이 바뀌므로 동시에 두 번 눌러도 신청 취소는 한 번만 돈다.
     *
     * <p>{@code @Modifying} 은 JPA 감사를 타지 않으므로 updatedAt 을 직접 넣는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Campaign c
               SET c.status = kr.givemeticket.api.campaign.domain.CampaignStatus.DELETED,
                   c.updatedAt = :now
             WHERE c.id = :id
               AND c.status <> kr.givemeticket.api.campaign.domain.CampaignStatus.DELETED
            """)
    int markDeletedIfNotDeleted(@Param("id") Long id, @Param("now") LocalDateTime now);
}
