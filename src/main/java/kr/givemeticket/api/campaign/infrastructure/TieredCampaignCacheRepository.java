package kr.givemeticket.api.campaign.infrastructure;

import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import lombok.RequiredArgsConstructor;

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
