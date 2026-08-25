package kr.givemeticket.api.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import kr.givemeticket.api.campaign.infrastructure.CampaignCacheInvalidation;
import kr.givemeticket.api.campaign.infrastructure.CampaignLocalCache;
import kr.givemeticket.api.campaign.infrastructure.NoOpCampaignCacheRepository;
import kr.givemeticket.api.campaign.infrastructure.RedisCampaignCacheRepository;
import kr.givemeticket.api.campaign.infrastructure.TieredCampaignCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 캐시 구성을 {@code campaign.cache.mode} 하나로 고른다.
 *
 * <ul>
 *   <li>{@code none} — 캐시 없음. Redis 를 아예 부르지 않는다
 *   <li>{@code redis} — Redis 캐시만
 *   <li>{@code local} — 로컬 캐시(L1) + Redis 캐시(L2) + pub/sub 무효화
 * </ul>
 *
 * <p>조건부 애노테이션을 구현체마다 흩뿌리지 않고 여기서 조립하는 이유는, 세 구성이
 * 서로 배타적이고 같은 빌드로 갈아 끼우며 비교하는 것이 목적이기 때문이다.
 * 어떤 조합이 떴는지는 기동 로그 한 줄로 남긴다.
 */
@Slf4j
@Configuration
public class CampaignCacheConfig {

    public enum Mode {
        NONE, REDIS, LOCAL;

        static Mode from(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "campaign.cache.mode 는 none / redis / local 중 하나여야 한다: " + value);
            }
        }
    }

    @Bean
    @ConditionalOnProperty(name = "campaign.cache.mode", havingValue = "local")
    public CampaignLocalCache campaignLocalCache(
            @Value("${campaign.cache.local.ttl:30s}") Duration ttl,
            @Value("${campaign.cache.local.max-size:1000}") long maxSize,
            MeterRegistry meterRegistry
    ) {
        return new CampaignLocalCache(ttl, maxSize, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "campaign.cache.mode", havingValue = "local")
    public CampaignCacheInvalidation campaignCacheInvalidation(
            StringRedisTemplate stringRedisTemplate,
            CampaignLocalCache campaignLocalCache,
            MeterRegistry meterRegistry
    ) {
        return new CampaignCacheInvalidation(stringRedisTemplate, campaignLocalCache, meterRegistry);
    }

    /**
     * 무효화 방송을 듣는 구독자. 컨테이너가 끊기면 Spring 이 다시 붙지만,
     * 끊겨 있던 동안의 방송은 되돌아오지 않는다. 로컬 캐시 TTL 이 그 구멍을 메운다.
     */
    @Bean
    @ConditionalOnProperty(name = "campaign.cache.mode", havingValue = "local")
    public RedisMessageListenerContainer campaignCacheListenerContainer(
            RedisConnectionFactory connectionFactory,
            CampaignCacheInvalidation campaignCacheInvalidation
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(campaignCacheInvalidation, new ChannelTopic(CampaignCacheInvalidation.CHANNEL));
        return container;
    }

    @Bean
    public CampaignCacheRepository campaignCacheRepository(
            @Value("${campaign.cache.mode:redis}") String modeValue,
            @Value("${campaign.cache.ttl:10m}") Duration ttl,
            RedisTemplate<String, CampaignSnapshot> campaignCacheRedisTemplate,
            MeterRegistry meterRegistry,
            org.springframework.beans.factory.ObjectProvider<CampaignLocalCache> localCaches,
            org.springframework.beans.factory.ObjectProvider<CampaignCacheInvalidation> invalidations
    ) {
        Mode mode = Mode.from(modeValue);
        log.info("campaign cache mode: {}", mode);

        if (mode == Mode.NONE) {
            return new NoOpCampaignCacheRepository();
        }

        RedisCampaignCacheRepository remote =
                new RedisCampaignCacheRepository(campaignCacheRedisTemplate, meterRegistry, ttl);
        if (mode == Mode.REDIS) {
            return remote;
        }

        return new TieredCampaignCacheRepository(
                localCaches.getObject(), remote, invalidations.getObject());
    }
}
