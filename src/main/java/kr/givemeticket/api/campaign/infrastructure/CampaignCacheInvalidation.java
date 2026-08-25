package kr.givemeticket.api.campaign.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 로컬 캐시 무효화를 Redis pub/sub 으로 인스턴스 사이에 퍼뜨린다.
 *
 * <p>Redis 캐시는 지운 쪽이 지우면 끝이지만, 로컬 캐시는 인스턴스마다 따로 들고 있어서
 * 지운 인스턴스 말고 나머지가 낡은 값을 계속 준다. 그래서 "이 shortCode 를 버려라"를 방송한다.
 *
 * <p>메시지는 {@code 보낸이|shortCode} 형식이다. 보낸이를 넣는 이유는 자기가 쏜 방송을 되받아
 * 이미 지운 것을 또 지우지 않기 위해서다. 해로울 건 없지만 지표가 부풀어 히트율 해석이 흐려진다.
 *
 * <p><b>이 방송은 보장되지 않는다.</b> Redis pub/sub 은 at-most-once 라, 구독이 끊긴 사이에
 * 발행된 메시지는 다시 오지 않는다. 그래서 이것만 믿으면 안 되고
 * {@link CampaignLocalCache} 의 짧은 TTL 이 반드시 함께 있어야 한다.
 * 방송은 정합성을 보장하는 장치가 아니라 수렴을 앞당기는 최적화다.
 */
@Slf4j
public class CampaignCacheInvalidation implements MessageListener {

    public static final String CHANNEL = "campaign:cache:invalidate";
    private static final char SEPARATOR = '|';

    /** 인스턴스마다 다른 값. 자기 방송을 걸러내는 데만 쓴다. */
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
            // 방송이 실패해도 이 인스턴스의 캐시는 이미 지웠다. 다른 인스턴스는 TTL 로 갈린다.
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
