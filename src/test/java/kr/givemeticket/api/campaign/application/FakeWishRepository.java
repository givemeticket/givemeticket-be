package kr.givemeticket.api.campaign.application;

import java.util.ArrayList;
import java.util.List;
import kr.givemeticket.api.wish.domain.Wish;
import kr.givemeticket.api.wish.domain.WishRepository;

/**
 * 찜을 넣은 순서대로 들고 있는다. 이 패키지의 테스트가 쓰는 조회만 채운다.
 */
class FakeWishRepository implements WishRepository {

    private final List<Wish> wishes = new ArrayList<>();

    void put(Long userId, Long campaignId) {
        wishes.add(new Wish(userId, campaignId));
    }

    @Override
    public List<Wish> findAllByUserIdLatestFirst(Long userId) {
        return wishes.reversed().stream()
                .filter(wish -> wish.getUserId().equals(userId))
                .toList();
    }

    @Override
    public List<Long> findUserIdsByCampaignId(Long campaignId) {
        return wishes.stream()
                .filter(wish -> wish.getCampaignId().equals(campaignId))
                .map(Wish::getUserId)
                .toList();
    }

    @Override
    public Wish save(Wish wish) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean exists(Long userId, Long campaignId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Long userId, Long campaignId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countByCampaignId(Long campaignId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteAllByUserId(Long userId) {
        throw new UnsupportedOperationException();
    }
}
