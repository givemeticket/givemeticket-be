package kr.givemeticket.api.campaign.infrastructure;

import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * 로컬 캐시(L1)를 앞에 두고 Redis 캐시(L2)를 뒤에 두는 2단 구성.
 *
 * <p>조회는 L1 → L2 → (없으면 비어 있음) 순이다. L2 에서 찾으면 L1 에도 올려서
 * 같은 캠페인의 다음 요청은 네트워크를 타지 않는다.
 *
 * <p>무효화는 반대로 <b>가까운 곳부터</b> 지운다. L2 를 먼저 지우면 그 사이 L1 이 살아 있어
 * 낡은 값을 계속 주기 때문이다. 마지막에 방송을 쏴서 다른 인스턴스의 L1 도 지우게 한다.
 */
@RequiredArgsConstructor
public class TieredCampaignCacheRepository implements CampaignCacheRepository {

    private final CampaignLocalCache localCache;
    private final CampaignCacheRepository remoteCache;
    private final CampaignCacheInvalidation invalidation;

    @Override
    public Optional<CampaignSnapshot> find(String shortCode) {
        Optional<CampaignSnapshot> local = localCache.find(shortCode);
        if (local.isPresent()) {
            return local;
        }

        Optional<CampaignSnapshot> remote = remoteCache.find(shortCode);
        remote.ifPresent(localCache::put);
        return remote;
    }

    @Override
    public void save(CampaignSnapshot snapshot) {
        remoteCache.save(snapshot);
        localCache.put(snapshot);
    }

    @Override
    public void evict(String shortCode) {
        localCache.evict(shortCode);
        remoteCache.evict(shortCode);
        invalidation.publish(shortCode);
    }
}
