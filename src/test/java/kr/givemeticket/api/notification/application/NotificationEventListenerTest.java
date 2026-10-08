package kr.givemeticket.api.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.CampaignOpenedEvent;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 오픈 스케줄러는 1초마다 돈다. 같은 오픈에는 원본이 하나만, 미뤘다가 다시 열리면 하나 더 생겨야 한다.
 */
class NotificationEventListenerTest {

    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 10, 10, 12, 0);

    private final FakeNotificationOutboxRepository outboxRepository = new FakeNotificationOutboxRepository();
    private final NotificationEventListener listener = new NotificationEventListener(
            outboxRepository, new FakeNotificationRepository(), new FakeCampaignRepository(),
            new OutboxEnqueuer(outboxRepository));

    @Test
    @DisplayName("같은 오픈이 두 번 들어와도 원본은 하나다")
    void enqueuesOpenedOnce() {
        listener.on(new CampaignOpenedEvent(1L, "행사", "code", OPEN_AT));
        listener.on(new CampaignOpenedEvent(1L, "행사", "code", OPEN_AT));

        assertThat(outboxRepository.rows).singleElement().satisfies(outbox -> {
            assertThat(outbox.getType()).isEqualTo(NotificationType.WISHED_CAMPAIGN_OPENED);
            assertThat(outbox.getDedupeKey()).isEqualTo("opened:1:2026-10-10T12:00:00Z");
        });
    }

    @Test
    @DisplayName("오픈을 미뤘다가 다시 열리면 원본이 하나 더 생긴다")
    void enqueuesAgainForNewOpenAt() {
        listener.on(new CampaignOpenedEvent(1L, "행사", "code", OPEN_AT));
        listener.on(new CampaignOpenedEvent(1L, "행사", "code", OPEN_AT.plusHours(3)));

        assertThat(outboxRepository.rows).extracting(NotificationOutbox::getDedupeKey)
                .doesNotHaveDuplicates()
                .hasSize(2);
    }
}
