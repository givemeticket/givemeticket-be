package kr.givemeticket.api.wish.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WishRepository {

    /**
     * 곧바로 flush 한다. 같은 찜이 동시에 들어오면 유니크 위반이 여기서 터져야 호출자가 잡을 수 있다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 이미 찜한 경우
     */
    Wish save(Wish wish);

    boolean exists(Long userId, Long campaignId);

    /** @return 지운 행 수. 찜하지 않은 행사면 0 */
    int delete(Long userId, Long campaignId);

    long countByCampaignId(Long campaignId);

    /** 행사별 찜 수. 찜이 하나도 없는 행사는 결과에 없다. */
    Map<Long, Long> countByCampaignIds(Collection<Long> campaignIds);

    /** 주어진 행사 중 이 사람이 찜한 것. */
    Set<Long> findWishedCampaignIds(Long userId, Collection<Long> campaignIds);

    /** 내 찜을 최근에 찜한 것부터. */
    List<Wish> findAllByUserIdLatestFirst(Long userId);

    /** 이 행사를 찜한 사람들. 오픈 알림을 받을 사람을 고른다. */
    List<Long> findUserIdsByCampaignId(Long campaignId);

    /** 탈퇴한 사용자의 찜을 지운다. */
    int deleteAllByUserId(Long userId);
}
