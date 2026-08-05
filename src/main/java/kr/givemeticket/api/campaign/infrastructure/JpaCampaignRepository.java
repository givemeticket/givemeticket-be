package kr.givemeticket.api.campaign.infrastructure;

import java.time.LocalDateTime;
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
    public List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now) {
        return springDataJpaCampaignRepository.findAllByStatusAndOpenAtLessThanEqual(status, now);
    }
}
