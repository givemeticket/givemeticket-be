package kr.givemeticket.api.campaign.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 캠페인 상세 조회용 Redis 캐시.
 *
 * <p>캐시는 거들 뿐이라 Redis 가 죽거나 값이 깨져도 조회는 성공해야 한다. 그래서 이 클래스의
 * 모든 실패는 로그와 지표로만 남기고 캐시 미스로 되돌린다.
 *
 * <p>지표
 * <ul>
 *   <li>{@code campaign_cache_requests_total{result="hit|miss|error"}} — 히트율
 *   <li>{@code campaign_cache_get_seconds} — Redis 왕복 + 압축 해제에 걸린 시간
 *   <li>{@code campaign_cache_value_size_bytes{state="raw|compressed"}} — 압축률
 * </ul>
 */
@Slf4j
public class RedisCampaignCacheRepository implements CampaignCacheRepository {

    private static final String KEY_PREFIX = "campaign:detail:";

    private final RedisTemplate<String, CampaignSnapshot> redisTemplate;
    private final Duration ttl;
    private final Timer getTimer;
    private final Counter hits;
    private final Counter misses;
    private final Counter errors;

    public RedisCampaignCacheRepository(
            RedisTemplate<String, CampaignSnapshot> campaignCacheRedisTemplate,
            MeterRegistry meterRegistry,
            Duration ttl
    ) {
        this.redisTemplate = campaignCacheRedisTemplate;
        this.ttl = ttl;
        this.getTimer = Timer.builder("campaign.cache.get")
                .description("캐시 조회 한 번에 걸린 시간. Redis 왕복과 압축 해제가 모두 포함된다")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        this.hits = counter(meterRegistry, "hit");
        this.misses = counter(meterRegistry, "miss");
        this.errors = counter(meterRegistry, "error");
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("campaign.cache.requests")
                .description("캠페인 캐시 조회 결과")
                .tag("tier", "redis")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public Optional<CampaignSnapshot> find(String shortCode) {
        try {
            CampaignSnapshot cached = getTimer.record(() -> redisTemplate.opsForValue().get(key(shortCode)));

            if (cached == null) {
                misses.increment();
                return Optional.empty();
            }
            hits.increment();
            return Optional.of(cached);
        } catch (RuntimeException e) {
            errors.increment();
            log.warn("campaign cache read failed: shortCode={}, reason={}", shortCode, e.toString());
            evict(shortCode);
            return Optional.empty();
        }
    }

    @Override
    public void save(CampaignSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(key(snapshot.shortCode()), snapshot, ttl);
        } catch (RuntimeException e) {
            errors.increment();
            log.warn("campaign cache write failed: shortCode={}, reason={}", snapshot.shortCode(), e.toString());
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            redisTemplate.delete(key(shortCode));
        } catch (RuntimeException e) {
            // 지우지 못한 캐시는 TTL 이 만료될 때까지 낡은 값을 준다. 조용히 넘기면 안 된다.
            errors.increment();
            log.error("campaign cache evict failed: shortCode={}", shortCode, e);
        }
    }

    private String key(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}
