package kr.givemeticket.api.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.CampaignChange.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수정 API 는 폼 전체를 받는다. 알림은 "보낸 값"이 아니라 "실제로 달라진 값"에만 나가야 한다.
 */
class CampaignNoticeTest {

    private static final LocalDateTime EVENT_AT = LocalDateTime.of(2026, 10, 10, 10, 0);

    @Test
    @DisplayName("같은 값이면 변경이 없다")
    void noChangeForSameValues() {
        CampaignNotice notice = new CampaignNotice("행사", EVENT_AT, null, "A홀", "서울시");

        assertThat(notice.diff(new CampaignNotice("행사", EVENT_AT, null, "A홀", "서울시"))).isEmpty();
    }

    @Test
    @DisplayName("일시는 UTC 문자열(Z)로 담긴다")
    void formatsDateTimeAsUtc() {
        CampaignNotice before = new CampaignNotice("행사", EVENT_AT, null, null, null);
        CampaignNotice after = new CampaignNotice("행사", EVENT_AT.plusHours(2), null, null, null);

        assertThat(before.diff(after)).containsExactly(new CampaignChange(
                Field.EVENT_AT, "2026-10-10T10:00:00Z", "2026-10-10T12:00:00Z"));
    }

    @Test
    @DisplayName("값을 지우면 after 가 null 이다")
    void reportsClearedValue() {
        CampaignNotice before = new CampaignNotice("행사", null, null, "A홀", "서울시");
        CampaignNotice after = new CampaignNotice("행사", null, null, null, null);

        assertThat(before.diff(after)).containsExactly(
                new CampaignChange(Field.LOCATION, "A홀", null),
                new CampaignChange(Field.ADDRESS, "서울시", null));
    }

    @Test
    @DisplayName("안내 정보가 없는 행사도 제목은 비교한다")
    void comparesTitleWithoutDetail() {
        Campaign campaign = new Campaign(
                1L, "code", "옛 제목", CampaignType.TICKET, 10, EVENT_AT, null);
        CampaignNotice before = CampaignNotice.of(campaign);

        campaign.changeTitle("새 제목");

        assertThat(before.diff(CampaignNotice.of(campaign))).containsExactly(
                new CampaignChange(Field.TITLE, "옛 제목", "새 제목"));
    }
}
