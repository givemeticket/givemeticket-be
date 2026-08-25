package kr.givemeticket.api.campaign.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;

/**
 * 프로세스 안에 두는 1차 캐시.
 *
 * <p>Redis 캐시가 이미 DB 왕복을 없앴다면, 이 캐시가 없애는 것은 <b>Redis 왕복과 압축 해제</b>다.
 * 히트하면 네트워크도 gunzip 도 없이 이미 만들어 둔 객체를 그대로 돌려준다.
 *
 * <p>대신 값이 인스턴스마다 따로 산다. 그래서 두 가지 안전장치를 같이 둔다.
 * <ul>
 *   <li>변경이 생기면 pub/sub 으로 모든 인스턴스에 알린다 ({@link CampaignCacheInvalidation})
 *   <li>그 알림을 놓쳐도 낡은 값이 영원히 남지 않도록 TTL 을 짧게 잡는다
 * </ul>
 * pub/sub 은 at-most-once 라 구독이 끊긴 사이의 알림은 다시 오지 않는다. TTL 이 최후의 보루다.
 *
 * <p>크기도 묶어 둔다. 로컬 캐시는 힙에 그대로 얹히기 때문에, 캠페인이 많아지면
 * old 영역에 상주하는 양이 늘어 GC 에 그대로 나타난다.
 */
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
