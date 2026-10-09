package kr.givemeticket.api.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignDetail;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationType;
import kr.givemeticket.api.user.application.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스케줄러는 1분마다 돈다. 몇 번을 돌아도 한 행사에 한 번만 나가야 하고,
 * 시작 일시가 바뀌면 새 일시 기준으로 한 번 더 나가야 한다.
 */
class EventReminderSchedulerTest {

    private static final Long CAMPAIGN_ID = 1L;

    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakeNotificationOutboxRepository outboxRepository = new FakeNotificationOutboxRepository();
    private final EventReminderScheduler scheduler =
            new EventReminderScheduler(campaignRepository, outboxRepository,
                    new NotificationPayloads(campaignRepository, new UserService(userRepository, null)));

    @Test
    @DisplayName("하루 안에 시작하는 행사는 원본이 한 번만 생긴다")
    void enqueuesOnce() {
        Campaign campaign = givenCampaign(LocalDateTime.now().plusHours(5));

        scheduler.enqueueReminders();
        scheduler.enqueueReminders();

        assertThat(outboxRepository.rows).singleElement().satisfies(outbox -> {
            assertThat(outbox.getType()).isEqualTo(NotificationType.EVENT_REMINDER);
            assertThat(outbox.getCampaignId()).isEqualTo(campaign.getId());
            assertThat(outbox.getPayload().ownerNickname()).isEqualTo("주최자");
        });
    }

    @Test
    @DisplayName("하루보다 먼 행사는 아직 아니다")
    void skipsFarEvent() {
        givenCampaign(LocalDateTime.now().plusDays(2));

        scheduler.enqueueReminders();

        assertThat(outboxRepository.rows).isEmpty();
    }

    @Test
    @DisplayName("시작 일시가 바뀌면 새 일시로 한 번 더 나간다")
    void enqueuesAgainWhenEventAtChanges() {
        Campaign campaign = givenCampaign(LocalDateTime.now().plusHours(5));
        scheduler.enqueueReminders();

        campaign.changeDetail(detail(LocalDateTime.now().plusHours(8)));
        scheduler.enqueueReminders();

        assertThat(outboxRepository.rows).extracting(NotificationOutbox::getDedupeKey)
                .doesNotHaveDuplicates()
                .hasSize(2);
    }

    @Test
    @DisplayName("삭제된 행사는 보내지 않는다")
    void skipsDeletedCampaign() {
        Campaign campaign = givenCampaign(LocalDateTime.now().plusHours(5));
        TestEntities.with(campaign, "status", CampaignStatus.DELETED);

        scheduler.enqueueReminders();

        assertThat(outboxRepository.rows).isEmpty();
    }

    private Campaign givenCampaign(LocalDateTime eventAt) {
        userRepository.put(10L, "주최자");
        Campaign campaign = new Campaign(
                10L, "code", "행사", CampaignType.TICKET, 10,
                LocalDateTime.now().minusDays(1), detail(eventAt));
        TestEntities.with(campaign, "id", CAMPAIGN_ID);
        TestEntities.with(campaign, "status", CampaignStatus.OPEN);
        campaignRepository.put(campaign);
        return campaign;
    }

    private static CampaignDetail detail(LocalDateTime eventAt) {
        return new CampaignDetail(null, eventAt.withNano(0), null, "A홀", null, null, null, null);
    }
}
