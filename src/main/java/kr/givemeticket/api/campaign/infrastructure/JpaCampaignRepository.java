package kr.givemeticket.api.campaign.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaCampaignRepository implements CampaignRepository {

    private final SpringDataJpaCampaignRepository springDataJpaCampaignRepository;

    @Override
    public Campaign save(Campaign campaign) {
        return springDataJpaCampaignRepository.save(campaign);
    }

    @Override
    public Optional<Campaign> findById(Long campaignId) {
        return springDataJpaCampaignRepository.findById(campaignId);
    }

    @Override
    public Optional<Campaign> findByShortCode(String shortCode) {
        return springDataJpaCampaignRepository.findByShortCode(shortCode);
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        return springDataJpaCampaignRepository.existsByShortCode(shortCode);
    }

    @Override
    public List<Campaign> findAllOwnedBy(Long ownerId) {
        return springDataJpaCampaignRepository.findAllByOwnerIdAndStatusNotOrderByIdDesc(
                ownerId, CampaignStatus.DELETED);
    }

    @Override
    public List<Campaign> findAllByIdIn(Collection<Long> campaignIds) {
        if (campaignIds.isEmpty()) {
            return List.of();
        }
        return springDataJpaCampaignRepository.findAllByIdInOrderByIdDesc(campaignIds);
    }

    @Override
    public List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now) {
        return springDataJpaCampaignRepository.findAllByStatusAndOpenAtLessThanEqual(status, now);
    }
}
