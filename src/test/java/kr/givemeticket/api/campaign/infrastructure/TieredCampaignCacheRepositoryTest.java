package kr.givemeticket.api.campaign.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TieredCampaignCacheRepositoryTest {

    private static final String SHORT_CODE = "abcd1234";

    private MeterRegistry meterRegistry;
    private CampaignLocalCache localCache;
    private CampaignCacheRepository remoteCache;
    private CampaignCacheInvalidation invalidation;
    private TieredCampaignCacheRepository tiered;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        localCache = new CampaignLocalCache(Duration.ofSeconds(30), 100, meterRegistry);
        remoteCache = mock(CampaignCacheRepository.class);
        invalidation = mock(CampaignCacheInvalidation.class);
        tiered = new TieredCampaignCacheRepository(localCache, remoteCache, invalidation);
    }

    @Test
    @DisplayName("로컬에 있으면 Redis 를 부르지 않는다")
    void localHitSkipsRemote() {
        localCache.put(snapshot());

        assertThat(tiered.find(SHORT_CODE)).isPresent();

        verify(remoteCache, never()).find(anyString());
    }

    @Test
    @DisplayName("로컬에 없으면 Redis 에서 찾고, 찾은 값을 로컬에도 올린다")
    void remoteHitFillsLocal() {
        when(remoteCache.find(SHORT_CODE)).thenReturn(Optional.of(snapshot()));

        assertThat(tiered.find(SHORT_CODE)).isPresent();
        // 두 번째 조회는 로컬에서 끝나야 한다
        assertThat(tiered.find(SHORT_CODE)).isPresent();

        verify(remoteCache).find(SHORT_CODE);
        assertThat(hitCount("local")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("둘 다 없으면 빈 값이다")
    void bothMiss() {
        when(remoteCache.find(SHORT_CODE)).thenReturn(Optional.empty());

        assertThat(tiered.find(SHORT_CODE)).isEmpty();
    }

    @Test
    @DisplayName("무효화하면 로컬·Redis 를 지우고 방송까지 쏜다")
    void evictClearsBothTiersAndBroadcasts() {
        localCache.put(snapshot());

        tiered.evict(SHORT_CODE);

        assertThat(localCache.find(SHORT_CODE)).isEmpty();
        verify(remoteCache).evict(SHORT_CODE);
        verify(invalidation).publish(SHORT_CODE);
    }

    @Test
    @DisplayName("저장하면 Redis 와 로컬에 함께 올린다")
    void saveFillsBothTiers() {
        tiered.save(snapshot());

        verify(remoteCache).save(any(CampaignSnapshot.class));
        assertThat(localCache.find(SHORT_CODE)).isPresent();
    }

    private double hitCount(String tier) {
        return meterRegistry.get("campaign.cache.requests")
                .tag("tier", tier).tag("result", "hit").counter().count();
    }

    private CampaignSnapshot snapshot() {
        return new CampaignSnapshot(
                1L, 2L, SHORT_CODE, "제목", CampaignType.TICKET, 100,
                LocalDateTime.of(2026, 9, 1, 12, 0), true, CampaignStatus.OPEN, null);
    }
}
