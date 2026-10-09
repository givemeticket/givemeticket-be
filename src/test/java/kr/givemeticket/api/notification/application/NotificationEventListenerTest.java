package kr.givemeticket.api.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.apply.domain.ApplicationCancelledByOwnerEvent;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignChange;
import kr.givemeticket.api.campaign.domain.CampaignChangedEvent;
import kr.givemeticket.api.campaign.domain.CampaignClosedEvent;
import kr.givemeticket.api.campaign.domain.CampaignOpenedEvent;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationType;
import kr.givemeticket.api.user.application.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이벤트가 어떤 원본으로 남는가. 모든 알림에 그때의 주최자 닉네임이 담겨야 하고,
 * 오픈 스케줄러는 1초마다 돌아도 같은 오픈에 원본을 하나만 남겨야 한다.
 */
class NotificationEventListenerTest {

    private static final Long CAMPAIGN_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 10, 10, 12, 0);

    private final FakeNotificationOutboxRepository outboxRepository = new FakeNotificationOutboxRepository();
    private final FakeNotificationRepository notificationRepository = new FakeNotificationRepository();
    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeUserRepository userRepository = new FakeUserRepository();

    private final NotificationEventListener listener = new NotificationEventListener(
            outboxRepository,
            notificationRepository,
            new NotificationPayloads(campaignRepository, new UserService(userRepository, null)),
            new OutboxEnqueuer(outboxRepository));

    @BeforeEach
    void givenCampaign() {
        Campaign campaign = new Campaign(OWNER_ID, "code", "가을 북토크", CampaignType.TICKET, 10, OPEN_AT, null);
        campaignRepository.put(TestEntities.with(campaign, "id", CAMPAIGN_ID));
        userRepository.put(OWNER_ID, "spring_popup");
    }

    @Test
    @DisplayName("같은 오픈이 두 번 들어와도 원본은 하나다")
    void enqueuesOpenedOnce() {
        listener.on(new CampaignOpenedEvent(CAMPAIGN_ID, "가을 북토크", "code", OPEN_AT));
        listener.on(new CampaignOpenedEvent(CAMPAIGN_ID, "가을 북토크", "code", OPEN_AT));

        assertThat(outboxRepository.rows).singleElement().satisfies(outbox -> {
            assertThat(outbox.getType()).isEqualTo(NotificationType.WISHED_CAMPAIGN_OPENED);
            assertThat(outbox.getDedupeKey()).isEqualTo("opened:1:2026-10-10T12:00:00Z");
        });
    }

    @Test
    @DisplayName("오픈을 미뤘다가 다시 열리면 원본이 하나 더 생긴다")
    void enqueuesAgainForNewOpenAt() {
        listener.on(new CampaignOpenedEvent(CAMPAIGN_ID, "가을 북토크", "code", OPEN_AT));
        listener.on(new CampaignOpenedEvent(CAMPAIGN_ID, "가을 북토크", "code", OPEN_AT.plusHours(3)));

        assertThat(outboxRepository.rows).extracting(NotificationOutbox::getDedupeKey)
                .doesNotHaveDuplicates()
                .hasSize(2);
    }

    @Test
    @DisplayName("종료는 종료 시각과 주최자 닉네임을 담은 원본으로 남는다")
    void enqueuesClosedWithClosedAt() {
        listener.on(new CampaignClosedEvent(CAMPAIGN_ID, LocalDateTime.of(2026, 9, 10, 13, 0)));

        assertThat(outboxRepository.rows).singleElement().satisfies(outbox -> {
            assertThat(outbox.getType()).isEqualTo(NotificationType.CAMPAIGN_CLOSED);
            assertThat(outbox.getPayload().closedAt()).isEqualTo("2026-09-10T13:00:00Z");
            assertThat(outbox.getPayload().ownerNickname()).isEqualTo("spring_popup");
            assertThat(outbox.getPayload().campaignTitle()).isEqualTo("가을 북토크");
        });
    }

    @Test
    @DisplayName("변경 알림에도 주최자 닉네임이 담긴다")
    void enqueuesChangedWithOwnerNickname() {
        listener.on(new CampaignChangedEvent(CAMPAIGN_ID, "가을 북토크", "code", List.of(
                new CampaignChange(CampaignChange.Field.OPEN_AT, "2026-10-10T12:00:00Z", "2026-10-11T12:00:00Z"))));

        assertThat(outboxRepository.rows).singleElement().satisfies(outbox -> {
            assertThat(outbox.getPayload().ownerNickname()).isEqualTo("spring_popup");
            assertThat(outbox.getPayload().changes()).singleElement()
                    .satisfies(change -> assertThat(change.field()).isEqualTo("OPEN_AT"));
        });
    }

    @Test
    @DisplayName("주최자 취소는 원본 없이 곧바로 알림함에 들어간다")
    void savesCancelledDirectly() {
        listener.on(new ApplicationCancelledByOwnerEvent(101L, CAMPAIGN_ID, 7L));

        assertThat(outboxRepository.rows).isEmpty();
        assertThat(notificationRepository.of(7L)).singleElement().satisfies(notification ->
                assertThat(notification.getPayload().ownerNickname()).isEqualTo("spring_popup"));
    }
}
