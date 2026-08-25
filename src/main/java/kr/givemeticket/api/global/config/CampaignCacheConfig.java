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
