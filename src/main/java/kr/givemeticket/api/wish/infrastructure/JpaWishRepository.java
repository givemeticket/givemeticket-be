package kr.givemeticket.api.wish.infrastructure;

import java.util.List;
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
