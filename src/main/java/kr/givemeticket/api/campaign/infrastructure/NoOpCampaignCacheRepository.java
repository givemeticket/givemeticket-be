package kr.givemeticket.api.campaign.infrastructure;

import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;

public class NoOpCampaignCacheRepository implements CampaignCacheRepository {

    @Override
    public Optional<CampaignSnapshot> find(String shortCode) {
        return Optional.empty();
    }

    @Override
    public void save(CampaignSnapshot snapshot) {
    }

    @Override
    public void evict(String shortCode) {
    }
}
