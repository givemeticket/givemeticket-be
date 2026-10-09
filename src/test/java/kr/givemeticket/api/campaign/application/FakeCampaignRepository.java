package kr.givemeticket.api.campaign.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignCursor;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignSearchCondition;
import kr.givemeticket.api.campaign.domain.CampaignType;
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
    public List<Campaign> findAllLiveByEventAtBetween(LocalDateTime from, LocalDateTime to) {
        throw new UnsupportedOperationException();
    }

    /** 매진 판정은 확정 신청 수를 세는 대신 여기 심어 둔 id 로 대신한다. */
    final Set<Long> soldOutIds = new HashSet<>();

    /**
     * 실제 쿼리와 같은 규칙: 삭제 제외, 제목 대소문자 무시 부분 일치, 오픈 시각 [from, to),
     * 상태 칩 OR(진행중은 매진 제외), 정렬과 커서.
     */
    @Override
    public List<Campaign> search(CampaignSearchCondition condition, CampaignCursor cursor, int limit) {
        Comparator<Campaign> order = switch (condition.sort()) {
            case LATEST -> Comparator.comparing(Campaign::getId).reversed();
            case OPEN_AT_ASC -> Comparator.comparing(Campaign::getOpenAt).thenComparing(Campaign::getId);
            case OPEN_AT_DESC -> Comparator.comparing(Campaign::getOpenAt).thenComparing(Campaign::getId).reversed();
        };
        return matching(condition)
                .filter(campaign -> cursor == null || order.compare(campaign, cursorCampaign(cursor)) > 0)
                .sorted(order)
                .limit(limit)
                .toList();
    }

    @Override
    public long count(CampaignSearchCondition condition) {
        return matching(condition).count();
    }

    @Override
    public List<Campaign> findAllScheduledByOpenAtBetween(LocalDateTime from, LocalDateTime to) {
        throw new UnsupportedOperationException();
    }

    private Stream<Campaign> matching(CampaignSearchCondition condition) {
        return campaigns.values().stream()
                .filter(campaign -> !campaign.isDeleted())
                .filter(campaign -> condition.keyword() == null || campaign.getTitle().toLowerCase(Locale.ROOT)
                        .contains(condition.keyword().toLowerCase(Locale.ROOT)))
                .filter(campaign -> condition.openFrom() == null
                        || !campaign.getOpenAt().isBefore(condition.openFrom()))
                .filter(campaign -> condition.openTo() == null || campaign.getOpenAt().isBefore(condition.openTo()))
                .filter(campaign -> !condition.filtersState() || matchesChip(campaign, condition));
    }

    private boolean matchesChip(Campaign campaign, CampaignSearchCondition condition) {
        boolean soldOut = soldOutIds.contains(campaign.getId());
        return switch (campaign.getStatus()) {
            case SCHEDULED -> condition.statuses().contains(CampaignStatus.SCHEDULED);
            case CLOSED -> condition.statuses().contains(CampaignStatus.CLOSED);
            case OPEN -> soldOut ? condition.soldOut() : condition.statuses().contains(CampaignStatus.OPEN);
            case DELETED -> false;
        };
    }

    /** 커서가 가리키는 자리를 비교용 캠페인으로 만든다. 정렬 기준값(id, openAt)만 의미가 있다. */
    private static Campaign cursorCampaign(CampaignCursor cursor) {
        Campaign marker = new Campaign(0L, "cursor", "cursor", CampaignType.TICKET, 1,
                cursor.openAt() == null ? LocalDateTime.MIN : cursor.openAt(), null);
        return TestEntities.with(marker, "id", cursor.id());
    }

    @Override
    public List<Campaign> findLivePageOwnedBy(Long ownerId, Long cursor, int limit) {
        return livePage(campaign -> campaign.isOwnedBy(ownerId), cursor, limit);
    }

    private List<Campaign> livePage(Predicate<Campaign> condition, Long cursor, int limit) {
        return campaigns.values().stream()
                .filter(campaign -> !campaign.isDeleted())
                .filter(condition)
                .filter(campaign -> cursor == null || campaign.getId() < cursor)
                .sorted(Comparator.comparing(Campaign::getId).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public int markDeleted(Long campaignId) {
        throw new UnsupportedOperationException();
    }
}
