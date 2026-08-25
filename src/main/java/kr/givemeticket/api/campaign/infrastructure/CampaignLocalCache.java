package kr.givemeticket.api.campaign.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;

public class CampaignLocalCache {

    private final Cache<String, CampaignSnapshot> cache;
    private final Counter hits;
    private final Counter misses;

    public CampaignLocalCache(Duration ttl, long maxSize, MeterRegistry meterRegistry) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
        this.hits = counter(meterRegistry, "hit");
        this.misses = counter(meterRegistry, "miss");

        Gauge.builder("campaign.local.cache.entries", cache, Cache::estimatedSize)
                .description("로컬 캐시에 들어 있는 항목 수. 힙에 상주하는 양을 가늠한다")
                .register(meterRegistry);
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("campaign.cache.requests")
                .description("캠페인 캐시 조회 결과")
                .tag("tier", "local")
                .tag("result", result)
                .register(registry);
    }

    public Optional<CampaignSnapshot> find(String shortCode) {
        CampaignSnapshot cached = cache.getIfPresent(shortCode);
        if (cached == null) {
            misses.increment();
            return Optional.empty();
        }
        hits.increment();
        return Optional.of(cached);
    }

    public void put(CampaignSnapshot snapshot) {
        cache.put(snapshot.shortCode(), snapshot);
    }

    public void evict(String shortCode) {
        cache.invalidate(shortCode);
    }
}
