package kr.givemeticket.api.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationType;
import kr.givemeticket.api.user.application.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 오픈 10분 전 알림. 30초마다 돌아도 한 오픈에 한 번, 오픈을 미루면 새 시각으로 한 번 더.
 */
class WishedOpeningSoonSchedulerTest {

    private static final Long CAMPAIGN_ID = 1L;

    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeNotificationOutboxRepository outboxRepository = new FakeNotificationOutboxRepository();
    private final WishedOpeningSoonScheduler scheduler = new WishedOpeningSoonScheduler(
            campaignRepository, outboxRepository,
            new NotificationPayloads(campaignRepository, new UserService(new FakeUserRepository(), null)));

    @Test
    @DisplayName("10분 안에 열리는 행사는 원본이 한 번만 생기고 오픈 시각이 담긴다")
    void enqueuesOnce() {
        Campaign campaign = givenCampaign(LocalDateTime.now().plusMinutes(5).withNano(0), CampaignStatus.SCHEDULED);

        scheduler.enqueueOpeningSoon();
        scheduler.enqueueOpeningSoon();

        assertThat(outboxRepository.rows).singleElement().satisfies(outbox -> {
            assertThat(outbox.getType()).isEqualTo(NotificationType.WISHED_CAMPAIGN_OPENING_SOON);
            assertThat(outbox.getPayload().openAt()).endsWith("Z");
            assertThat(outbox.getCampaignId()).isEqualTo(campaign.getId());
        });
    }

    @Test
    @DisplayName("10분보다 먼 행사와 이미 열린 행사는 아니다")
    void skipsFarOrOpened() {
        givenCampaign(LocalDateTime.now().plusMinutes(30), CampaignStatus.SCHEDULED);

        scheduler.enqueueOpeningSoon();

        assertThat(outboxRepository.rows).isEmpty();
    }

    @Test
    @DisplayName("오픈을 미루면 새 시각으로 한 번 더 나간다")
    void enqueuesAgainWhenOpenAtChanges() {
        Campaign campaign = givenCampaign(LocalDateTime.now().plusMinutes(5).withNano(0), CampaignStatus.SCHEDULED);
        scheduler.enqueueOpeningSoon();

        campaign.changeOpenAt(LocalDateTime.now().plusMinutes(8).withNano(0));
        scheduler.enqueueOpeningSoon();

        assertThat(outboxRepository.rows).extracting(NotificationOutbox::getDedupeKey)
                .doesNotHaveDuplicates()
                .hasSize(2);
    }

    private Campaign givenCampaign(LocalDateTime openAt, CampaignStatus status) {
        Campaign campaign = new Campaign(10L, "code", "행사", CampaignType.TICKET, 10, openAt, null);
        TestEntities.with(campaign, "id", CAMPAIGN_ID);
        TestEntities.with(campaign, "status", status);
        campaignRepository.put(campaign);
        return campaign;
    }
}
