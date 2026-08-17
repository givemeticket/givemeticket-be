package kr.givemeticket.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GzipRedisSerializerTest {

    private MeterRegistry meterRegistry;
    private GzipRedisSerializer<CampaignSnapshot> serializer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        serializer = new GzipRedisSerializer<>(
                CampaignSnapshot.class,
                GzipRedisSerializer.defaultObjectMapper(),
                meterRegistry,
                "campaign");
    }

    @Test
    @DisplayName("압축했다가 풀면 원래 값이 그대로 나온다")
    void roundTrip() {
        CampaignSnapshot original = snapshot("본문", LocalDateTime.of(2026, 9, 1, 19, 0));

        CampaignSnapshot restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("detail 이 없어도 왕복한다")
    void roundTripWithoutDetail() {
        CampaignSnapshot original = new CampaignSnapshot(
                1L, 2L, "abc", "제목", CampaignType.TICKET, 100,
                LocalDateTime.of(2026, 9, 1, 12, 0), true, CampaignStatus.OPEN, null);

        assertThat(serializer.deserialize(serializer.serialize(original))).isEqualTo(original);
    }

    @Test
    @DisplayName("null 과 빈 배열을 그대로 통과시킨다")
    void handlesEmpty() {
        assertThat(serializer.serialize(null)).isEmpty();
        assertThat(serializer.deserialize(new byte[0])).isNull();
        assertThat(serializer.deserialize(null)).isNull();
    }

    @Test
    @DisplayName("원본과 압축 후 크기를 둘 다 지표로 남긴다")
    void recordsBothSizes() {
        serializer.serialize(snapshot("가".repeat(3_000), LocalDateTime.of(2026, 9, 1, 19, 0)));

        double raw = meterRegistry.get("campaign.cache.value.size").tag("state", "raw").summary().totalAmount();
        double compressed = meterRegistry.get("campaign.cache.value.size")
                .tag("state", "compressed").summary().totalAmount();

        assertThat(raw).isPositive();
        assertThat(compressed).isPositive().isLessThan(raw);
    }

    private CampaignSnapshot snapshot(String content, LocalDateTime eventAt) {
        return new CampaignSnapshot(
                1L, 2L, "abcd1234", "행사 제목", CampaignType.TICKET, 100,
                LocalDateTime.of(2026, 9, 1, 12, 0), true, CampaignStatus.OPEN,
                new CampaignSnapshot.Detail(
                        content, eventAt, eventAt.plusHours(2),
                        "올림픽공원", "서울시 송파구", "https://img.example/1.png", "010-0000-0000", 15_000));
    }
}
