package kr.givemeticket.api.wish.infrastructure;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.givemeticket.api.wish.domain.Wish;
import kr.givemeticket.api.wish.domain.WishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaWishRepository implements WishRepository {

    private final SpringDataJpaWishRepository springDataJpaWishRepository;

    @Override
    public Wish save(Wish wish) {
        return springDataJpaWishRepository.saveAndFlush(wish);
    }

    @Override
    public boolean exists(Long userId, Long campaignId) {
        return springDataJpaWishRepository.existsByUserIdAndCampaignId(userId, campaignId);
    }

    @Override
    public int delete(Long userId, Long campaignId) {
        return springDataJpaWishRepository.deleteByUserIdAndCampaignId(userId, campaignId);
    }

    @Override
    public long countByCampaignId(Long campaignId) {
        return springDataJpaWishRepository.countByCampaignId(campaignId);
    }

    @Override
    public Map<Long, Long> countByCampaignIds(Collection<Long> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        return springDataJpaWishRepository.countGroupByCampaignId(campaignIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @Override
    public Set<Long> findWishedCampaignIds(Long userId, Collection<Long> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                springDataJpaWishRepository.findCampaignIdsByUserIdAndCampaignIdIn(userId, campaignIds));
    }

    @Override
    public List<Wish> findAllByUserIdLatestFirst(Long userId) {
        return springDataJpaWishRepository.findAllByUserIdOrderByIdDesc(userId);
    }

    @Override
    public List<Long> findUserIdsByCampaignId(Long campaignId) {
        return springDataJpaWishRepository.findUserIdsByCampaignId(campaignId);
    }

    @Override
    public int deleteAllByUserId(Long userId) {
        return springDataJpaWishRepository.deleteAllByUserId(userId);
    }
}
