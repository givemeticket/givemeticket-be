package kr.givemeticket.api.campaign.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
public class CampaignCacheInvalidation implements MessageListener {

    public static final String CHANNEL = "campaign:cache:invalidate";
    private static final char SEPARATOR = '|';

    private final String instanceId = UUID.randomUUID().toString();

    private final StringRedisTemplate stringRedisTemplate;
    private final CampaignLocalCache localCache;
    private final Counter published;
    private final Counter received;

    public CampaignCacheInvalidation(
            StringRedisTemplate stringRedisTemplate,
            CampaignLocalCache localCache,
            MeterRegistry meterRegistry
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.localCache = localCache;
        this.published = counter(meterRegistry, "published");
        this.received = counter(meterRegistry, "received");
    }

    private static Counter counter(MeterRegistry registry, String direction) {
        return Counter.builder("campaign.cache.invalidations")
                .description("로컬 캐시 무효화 방송")
                .tag("direction", direction)
                .register(registry);
    }

    public void publish(String shortCode) {
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, instanceId + SEPARATOR + shortCode);
            published.increment();
        } catch (RuntimeException e) {
            log.warn("cache invalidation publish failed: shortCode={}, reason={}", shortCode, e.toString());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        int separator = body.indexOf(SEPARATOR);
        if (separator < 0) {
            log.warn("cache invalidation message malformed: {}", body);
            return;
        }

        String sender = body.substring(0, separator);
        String shortCode = body.substring(separator + 1);

        if (instanceId.equals(sender)) {
            return;
        }

        localCache.evict(shortCode);
        received.increment();
        log.debug("local cache evicted by broadcast: shortCode={}", shortCode);
    }
}
