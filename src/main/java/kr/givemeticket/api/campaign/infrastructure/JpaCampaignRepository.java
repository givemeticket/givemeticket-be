package kr.givemeticket.api.campaign.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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
        return springDataJpaCampaignRepository.findAllByOwnerIdOrderByIdDesc(ownerId);
    }

    @Override
    public List<Campaign> findAllLiveOwnedBy(Long ownerId) {
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

    @Override
    public List<Campaign> searchLiveByTitle(String keyword, Long cursor, int limit) {
        return springDataJpaCampaignRepository.searchLiveByTitle(
                escapeLike(keyword), cursorOrMax(cursor), Limit.of(limit));
    }

    @Override
    public List<Campaign> findLivePageOwnedBy(Long ownerId, Long cursor, int limit) {
        return springDataJpaCampaignRepository.findLivePageOwnedBy(
                ownerId, cursorOrMax(cursor), Limit.of(limit));
    }

    /** LIKE 의 와일드카드를 글자로 바꾼다. 이스케이프 문자는 쿼리의 ESCAPE '!' 와 맞춘다. */
    static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** 첫 페이지는 커서가 없다. 조건을 둘로 나누지 않고 가장 큰 값으로 대신한다. */
    private static Long cursorOrMax(Long cursor) {
        return (cursor == null) ? Long.MAX_VALUE : cursor;
    }

    @Override
    public List<Campaign> findAllLiveByEventAtBetween(LocalDateTime from, LocalDateTime to) {
        return springDataJpaCampaignRepository.findAllLiveByEventAtBetween(from, to);
    }

    @Override
    public int markDeleted(Long campaignId) {
        return springDataJpaCampaignRepository.markDeletedIfNotDeleted(campaignId, LocalDateTime.now());
    }
}
