package kr.givemeticket.api.campaign.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

class CampaignCacheInvalidationTest {

    private static final String SHORT_CODE = "abcd1234";

    private StringRedisTemplate redisTemplate;
    private MeterRegistry meterRegistry;

    /** 실제 Redis 대신, 발행된 메시지를 그대로 잡아 둔다. */
    private String lastPublished;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redisTemplate = mock(StringRedisTemplate.class);
        doAnswer(invocation -> {
            lastPublished = invocation.getArgument(1);
            return 1L;
        }).when(redisTemplate).convertAndSend(eq(CampaignCacheInvalidation.CHANNEL), anyString());
    }

    @Test
    @DisplayName("다른 인스턴스가 쏜 방송을 받으면 로컬 캐시를 지운다")
    void evictsOnBroadcastFromAnotherInstance() {
        CampaignLocalCache localCache = localCache();
        CampaignCacheInvalidation receiver = invalidation(localCache);
        localCache.put(snapshot());

        // 다른 인스턴스가 쏜 것처럼 보낸이를 다른 값으로 만든다
        receiver.onMessage(message("other-instance|" + SHORT_CODE), null);

        assertThat(localCache.find(SHORT_CODE)).isEmpty();
        assertThat(counter("received")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("자기가 쏜 방송은 되받아도 무시한다")
    void ignoresOwnBroadcast() {
        CampaignLocalCache localCache = localCache();
        CampaignCacheInvalidation sender = invalidation(localCache);

        sender.publish(SHORT_CODE);
        localCache.put(snapshot());
        sender.onMessage(message(lastPublished), null);

        assertThat(localCache.find(SHORT_CODE)).isPresent();
        assertThat(counter("received")).isZero();
        assertThat(counter("published")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("형식이 깨진 메시지는 무시한다")
    void ignoresMalformedMessage() {
        CampaignLocalCache localCache = localCache();
        CampaignCacheInvalidation receiver = invalidation(localCache);
        localCache.put(snapshot());

        receiver.onMessage(message("보낸이도 구분자도 없는 메시지"), null);

        assertThat(localCache.find(SHORT_CODE)).isPresent();
        assertThat(counter("received")).isZero();
    }

    @Test
    @DisplayName("발행이 실패해도 예외를 밖으로 던지지 않는다")
    void survivesPublishFailure() {
        doAnswer(invocation -> {
            throw new IllegalStateException("redis down");
        }).when(redisTemplate).convertAndSend(anyString(), anyString());

        CampaignCacheInvalidation sender = invalidation(localCache());

        sender.publish(SHORT_CODE);

        assertThat(counter("published")).isZero();
    }

    private CampaignLocalCache localCache() {
        return new CampaignLocalCache(Duration.ofSeconds(30), 100, meterRegistry);
    }

    private CampaignCacheInvalidation invalidation(CampaignLocalCache localCache) {
        return new CampaignCacheInvalidation(redisTemplate, localCache, meterRegistry);
    }

    private DefaultMessage message(String body) {
        return new DefaultMessage(
                CampaignCacheInvalidation.CHANNEL.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }

    private double counter(String direction) {
        return meterRegistry.get("campaign.cache.invalidations")
                .tag("direction", direction).counter().count();
    }

    private CampaignSnapshot snapshot() {
        return new CampaignSnapshot(
                1L, 2L, SHORT_CODE, "제목", CampaignType.TICKET, 100,
                LocalDateTime.of(2026, 9, 1, 12, 0), CampaignStatus.OPEN, null);
    }
}
