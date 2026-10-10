package kr.givemeticket.api.wish.infrastructure;

import java.util.Collection;
import java.util.List;
import kr.givemeticket.api.wish.domain.Wish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SpringDataJpaWishRepository extends JpaRepository<Wish, Long> {

    boolean existsByUserIdAndCampaignId(Long userId, Long campaignId);

    long countByCampaignId(Long campaignId);

    List<Wish> findAllByUserIdOrderByIdDesc(Long userId);

    /** 행 하나가 [campaignId, count] 다. */
    @Query("SELECT w.campaignId, COUNT(w) FROM Wish w WHERE w.campaignId IN :campaignIds GROUP BY w.campaignId")
    List<Object[]> countGroupByCampaignId(@Param("campaignIds") Collection<Long> campaignIds);

    @Query("SELECT w.campaignId FROM Wish w WHERE w.userId = :userId AND w.campaignId IN :campaignIds")
    List<Long> findCampaignIdsByUserIdAndCampaignIdIn(
            @Param("userId") Long userId, @Param("campaignIds") Collection<Long> campaignIds);

    @Query("SELECT w.userId FROM Wish w WHERE w.campaignId = :campaignId")
    List<Long> findUserIdsByCampaignId(@Param("campaignId") Long campaignId);

    /** 찜 해제는 트랜잭션 밖에서 불린다. 한 문장이라 여기서 끊어도 된다. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Wish w WHERE w.userId = :userId AND w.campaignId = :campaignId")
    int deleteByUserIdAndCampaignId(@Param("userId") Long userId, @Param("campaignId") Long campaignId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Wish w WHERE w.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
