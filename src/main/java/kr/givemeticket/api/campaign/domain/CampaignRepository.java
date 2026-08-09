package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository {

    Campaign save(Campaign campaign);

    Optional<Campaign> findById(Long campaignId);

    Optional<Campaign> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * 내가 만든 행사 목록. 삭제된 캠페인은 빠진다.
     */
    List<Campaign> findAllOwnedBy(Long ownerId);

    List<Campaign> findAllByIdIn(Collection<Long> campaignIds);

    List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now);
}
