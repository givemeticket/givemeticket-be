package kr.givemeticket.api.campaign.domain;

import java.util.Optional;

public interface CampaignCacheRepository {

    Optional<CampaignSnapshot> find(String shortCode);

    void save(CampaignSnapshot snapshot);

    void evict(String shortCode);
}
