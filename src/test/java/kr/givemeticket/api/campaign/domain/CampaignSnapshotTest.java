package kr.givemeticket.api.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CampaignSnapshotTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Test
    @DisplayName("열려 있는 행사는 캐싱한다")
    void cachesOpenCampaign() {
        assertThat(snapshot(CampaignStatus.OPEN, null).isCacheable(NOW)).isTrue();
    }

    @Test
    @DisplayName("아직 열리지 않은 행사도 캐싱한다 — 오픈 직전이 가장 많이 조회된다")
    void cachesScheduledCampaign() {
        assertThat(snapshot(CampaignStatus.SCHEDULED, null).isCacheable(NOW)).isTrue();
    }

    @Test
    @DisplayName("삭제되거나 닫힌 행사는 캐싱하지 않는다")
    void skipsFinishedStatus() {
        assertThat(snapshot(CampaignStatus.DELETED, null).isCacheable(NOW)).isFalse();
        assertThat(snapshot(CampaignStatus.CLOSED, null).isCacheable(NOW)).isFalse();
    }

    @Test
    @DisplayName("행사 종료 시각이 지났으면 상태와 무관하게 캐싱하지 않는다")
    void skipsPastEvent() {
        assertThat(snapshot(CampaignStatus.OPEN, NOW.minusSeconds(1)).isCacheable(NOW)).isFalse();
    }

    @Test
    @DisplayName("종료 시각이 아직 남았으면 캐싱한다")
    void cachesOngoingEvent() {
        assertThat(snapshot(CampaignStatus.OPEN, NOW.plusHours(1)).isCacheable(NOW)).isTrue();
    }

    @Test
    @DisplayName("엔티티에서 뜬 스냅샷에 재고는 담기지 않는다")
    void doesNotCarryRemainingStock() {
        Campaign campaign = new Campaign(
                1L, "abcd1234", "제목", CampaignType.TICKET, 100, NOW, null);

        CampaignSnapshot snapshot = CampaignSnapshot.from(campaign);

        assertThat(snapshot.totalStock()).isEqualTo(100);
        assertThat(snapshot.status()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(snapshot.detail()).isNull();
    }

    private CampaignSnapshot snapshot(CampaignStatus status, LocalDateTime eventEndAt) {
        CampaignSnapshot.Detail detail = (eventEndAt == null)
                ? null
                : new CampaignSnapshot.Detail(null, null, eventEndAt, null, null, null, null, null);
        return new CampaignSnapshot(
                1L, 2L, "abcd1234", "제목", CampaignType.TICKET, 100, NOW, status, detail);
    }
}
