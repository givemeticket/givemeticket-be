package kr.givemeticket.api.campaign.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;

class FakeCampaignStateRepository implements CampaignStateRepository {

    final Map<Long, CampaignState> states = new LinkedHashMap<>();

    @Override
    public void save(Long campaignId, CampaignState state) {
        states.put(campaignId, state);
    }

    @Override
    public Optional<CampaignState> find(Long campaignId) {
        return Optional.ofNullable(states.get(campaignId));
    }

    @Override
    public void remove(Long campaignId) {
        states.remove(campaignId);
    }
}
