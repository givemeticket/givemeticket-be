package kr.givemeticket.api.campaign.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;

/**
 * 테스트가 실제로 쓰는 조회만 채운다. 나머지는 부르면 바로 터지게 두어,
 * 의도치 않은 경로를 탔을 때 조용히 지나가지 않게 한다.
 */
class FakeCampaignRepository implements CampaignRepository {

    private final Map<Long, Campaign> campaigns = new LinkedHashMap<>();

    void put(Long campaignId, Campaign campaign) {
        campaigns.put(campaignId, campaign);
    }

    @Override
    public Optional<Campaign> findById(Long campaignId) {
        return Optional.ofNullable(campaigns.get(campaignId));
    }

    @Override
    public Optional<Campaign> findByShortCode(String shortCode) {
        return campaigns.values().stream()
                .filter(campaign -> campaign.getShortCode().equals(shortCode))
                .findFirst();
    }

    @Override
    public List<Campaign> findAllOwnedBy(Long ownerId) {
        return new ArrayList<>(campaigns.values());
    }

    @Override
    public Campaign save(Campaign campaign) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Campaign> findAllLiveOwnedBy(Long ownerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Campaign> findAllByIdIn(Collection<Long> campaignIds) {
        return campaigns.values().stream()
                .filter(campaign -> campaignIds.contains(campaign.getId()))
                .toList();
    }

    @Override
    public List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int markDeleted(Long campaignId) {
        throw new UnsupportedOperationException();
    }
}
