package kr.givemeticket.api.notification.application;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;

/**
 * 알림이 쓰는 조회 둘만 채운다. 나머지는 부르면 바로 터진다.
 */
class FakeCampaignRepository implements CampaignRepository {

    private final Map<Long, Campaign> campaigns = new LinkedHashMap<>();

    void put(Campaign campaign) {
        campaigns.put(campaign.getId(), campaign);
    }

    @Override
    public Optional<Campaign> findById(Long campaignId) {
        return Optional.ofNullable(campaigns.get(campaignId));
    }

    /** 실제 쿼리와 같은 규칙: 시작 일시가 (from, to] 이고 삭제되지 않은 행사. */
    @Override
    public List<Campaign> findAllLiveByEventAtBetween(LocalDateTime from, LocalDateTime to) {
        return campaigns.values().stream()
                .filter(campaign -> !campaign.isDeleted())
                .filter(campaign -> campaign.getDetail() != null
                        && campaign.getDetail().getEventAt() != null)
                .filter(campaign -> campaign.getDetail().getEventAt().isAfter(from)
                        && !campaign.getDetail().getEventAt().isAfter(to))
                .toList();
    }

    @Override
    public Campaign save(Campaign campaign) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Campaign> findByShortCode(String shortCode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Campaign> findAllOwnedBy(Long ownerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Campaign> findAllLiveOwnedBy(Long ownerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Campaign> findAllByIdIn(Collection<Long> campaignIds) {
        throw new UnsupportedOperationException();
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
