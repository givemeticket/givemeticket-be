package kr.givemeticket.api.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.global.time.Utc;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationPayload;
import kr.givemeticket.api.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 원본 하나가 누구에게 펼쳐지는가. 같은 원본을 두 번 펼쳐도 알림이 늘지 않는가.
 */
class NotificationFanOutTest {

    private static final Long CAMPAIGN_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 1, 12, 0);

    private final FakeNotificationOutboxRepository outboxRepository = new FakeNotificationOutboxRepository();
    private final FakeNotificationRepository notificationRepository = new FakeNotificationRepository();
    private final FakeApplicationRepository applicationRepository = new FakeApplicationRepository();
    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeWishRepository wishRepository = new FakeWishRepository();

    private final NotificationFanOut fanOut = new NotificationFanOut(
            outboxRepository, notificationRepository, applicationRepository, campaignRepository,
            wishRepository);

    @Test
    @DisplayName("정보 변경은 확정된 신청자에게만 간다 — 취소한 사람과 주최자는 빠진다")
    void changedGoesToConfirmedApplicants() {
        givenCampaign();
        givenApplication(101L, 7L, ApplicationStatus.CONFIRMED, null);
        givenApplication(102L, 8L, ApplicationStatus.CANCELLED, null);
        givenApplication(103L, OWNER_ID, ApplicationStatus.CONFIRMED, null);
        givenOutbox(NotificationType.CAMPAIGN_CHANGED, null, null);

        assertThat(fanOut.fanOutNext()).isTrue();

        assertThat(notificationRepository.rows).extracting(Notification::getUserId).containsExactly(7L);
        assertThat(outboxRepository.rows.get(0).getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제는 아직 취소가 돌기 전인 신청과 삭제로 취소된 신청 모두에게 간다")
    void deletedGoesToBothConfirmedAndDeletionCancelled() {
        givenCampaign();
        givenApplication(101L, 7L, ApplicationStatus.CONFIRMED, null);
        givenApplication(102L, 8L, ApplicationStatus.CANCELLED, FailureReason.CAMPAIGN_DELETED);
        givenApplication(103L, 9L, ApplicationStatus.CANCELLED, null);
        givenOutbox(NotificationType.CAMPAIGN_DELETED, null, null);

        fanOut.fanOutNext();

        assertThat(notificationRepository.rows).extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    @DisplayName("처리한 원본은 다시 집히지 않는다")
    void processesOnce() {
        givenCampaign();
        givenApplication(101L, 7L, ApplicationStatus.CONFIRMED, null);
        givenOutbox(NotificationType.CAMPAIGN_CHANGED, null, null);

        fanOut.fanOutNext();

        assertThat(fanOut.fanOutNext()).isFalse();
        assertThat(notificationRepository.of(7L)).hasSize(1);
    }

    @Test
    @DisplayName("펼친 알림은 원본의 키를 공유해 같은 원본에서 두 번 생기지 않는다")
    void sharesFanOutKey() {
        givenCampaign();
        givenApplication(101L, 7L, ApplicationStatus.CONFIRMED, null);
        NotificationOutbox outbox = givenOutbox(NotificationType.CAMPAIGN_CHANGED, null, null);

        fanOut.fanOutNext();

        assertThat(notificationRepository.of(7L)).extracting(Notification::getDedupeKey)
                .containsExactly(outbox.fanOutKey());
    }

    @Test
    @DisplayName("이미 시작한 행사의 임박 알림은 보내지 않고 처리만 끝낸다")
    void skipsStaleReminder() {
        givenCampaign();
        givenApplication(101L, 7L, ApplicationStatus.CONFIRMED, null);
        String past = Utc.toInstant(LocalDateTime.now().minusMinutes(1)).toString();
        givenOutbox(NotificationType.EVENT_REMINDER, past, "reminder:D1:1:" + past);

        assertThat(fanOut.fanOutNext()).isTrue();

        assertThat(notificationRepository.rows).isEmpty();
        assertThat(outboxRepository.rows.get(0).getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("곧 시작할 행사의 임박 알림은 신청자에게 간다")
    void sendsUpcomingReminder() {
        givenCampaign();
        givenApplication(101L, 7L, ApplicationStatus.CONFIRMED, null);
        String upcoming = Utc.toInstant(LocalDateTime.now().plusHours(3)).toString();
        givenOutbox(NotificationType.EVENT_REMINDER, upcoming, "reminder:D1:1:" + upcoming);

        fanOut.fanOutNext();

        assertThat(notificationRepository.of(7L)).singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getType()).isEqualTo(NotificationType.EVENT_REMINDER);
                    assertThat(notification.getPayload().eventAt()).isEqualTo(upcoming);
                });
    }

    @Test
    @DisplayName("오픈 알림은 찜한 사람에게 간다 — 이미 신청한 사람과 주최자는 빠진다")
    void openedGoesToWishersNotYetApplied() {
        givenCampaign();
        wishRepository.put(7L, CAMPAIGN_ID);
        wishRepository.put(8L, CAMPAIGN_ID);
        wishRepository.put(OWNER_ID, CAMPAIGN_ID);
        wishRepository.put(9L, 2L);
        givenApplication(101L, 8L, ApplicationStatus.CONFIRMED, null);
        givenOutbox(NotificationType.WISHED_CAMPAIGN_OPENED, null, "opened:1:2026-10-01T12:00:00Z");

        fanOut.fanOutNext();

        assertThat(notificationRepository.rows).extracting(Notification::getUserId).containsExactly(7L);
        assertThat(notificationRepository.rows.get(0).getType())
                .isEqualTo(NotificationType.WISHED_CAMPAIGN_OPENED);
    }

    @Test
    @DisplayName("오픈 알림은 신청 취소한 찜 사용자에게도 간다 — 다시 신청할 수 있다")
    void openedGoesToWisherWhoCancelled() {
        givenCampaign();
        wishRepository.put(7L, CAMPAIGN_ID);
        givenApplication(101L, 7L, ApplicationStatus.CANCELLED, null);
        givenOutbox(NotificationType.WISHED_CAMPAIGN_OPENED, null, "opened:1:2026-10-01T12:00:00Z");

        fanOut.fanOutNext();

        assertThat(notificationRepository.rows).extracting(Notification::getUserId).containsExactly(7L);
    }

    @Test
    @DisplayName("변경 알림은 신청자와 찜한 사람 모두에게 한 번씩 간다")
    void changedGoesToApplicantsAndWishers() {
        givenCampaign();
        givenApplication(101L, 7L, ApplicationStatus.CONFIRMED, null);
        wishRepository.put(7L, CAMPAIGN_ID);
        wishRepository.put(8L, CAMPAIGN_ID);
        wishRepository.put(OWNER_ID, CAMPAIGN_ID);
        givenOutbox(NotificationType.CAMPAIGN_CHANGED, null, null);

        fanOut.fanOutNext();

        assertThat(notificationRepository.rows).extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    @DisplayName("종료 알림은 확정 신청자에게만 간다 — 찜한 사람은 받지 않는다")
    void closedGoesToConfirmedApplicantsOnly() {
        givenCampaign();
        givenApplication(101L, 7L, ApplicationStatus.CONFIRMED, null);
        givenApplication(102L, 8L, ApplicationStatus.CANCELLED, null);
        wishRepository.put(9L, CAMPAIGN_ID);
        givenOutbox(NotificationType.CAMPAIGN_CLOSED, null, null);

        fanOut.fanOutNext();

        assertThat(notificationRepository.rows).extracting(Notification::getUserId).containsExactly(7L);
    }

    @Test
    @DisplayName("오픈 임박 알림은 찜한 사람에게 가고, 오픈이 이미 지났으면 보내지 않는다")
    void openingSoonGoesToWishersUntilOpen() {
        givenCampaign();
        wishRepository.put(7L, CAMPAIGN_ID);
        String upcoming = Utc.toInstant(LocalDateTime.now().plusMinutes(5)).toString();
        String past = Utc.toInstant(LocalDateTime.now().minusMinutes(1)).toString();
        givenOutbox(NotificationType.WISHED_CAMPAIGN_OPENING_SOON, past, "opening-soon:1:" + past);
        givenOutbox(NotificationType.WISHED_CAMPAIGN_OPENING_SOON, upcoming, "opening-soon:1:" + upcoming);

        fanOut.fanOutNext();
        fanOut.fanOutNext();

        assertThat(notificationRepository.rows).singleElement().satisfies(notification -> {
            assertThat(notification.getUserId()).isEqualTo(7L);
            assertThat(notification.getPayload().openAt()).isEqualTo(upcoming);
        });
    }

    private void givenCampaign() {
        Campaign campaign = new Campaign(
                OWNER_ID, "code", "행사", CampaignType.TICKET, 10, BASE, null);
        TestEntities.with(campaign, "id", CAMPAIGN_ID);
        campaignRepository.put(campaign);
    }

    private void givenApplication(
            Long applicationId, Long userId, ApplicationStatus status, FailureReason reason) {
        Application application = Application.confirmed(applicationId, CAMPAIGN_ID, userId, BASE);
        TestEntities.with(application, "status", status);
        TestEntities.with(application, "failureReason", reason);
        applicationRepository.put(application);
    }

    /** @param dueAt 임박 알림의 기준 시각(UTC). 임박 알림이 아니면 null */
    private NotificationOutbox givenOutbox(NotificationType type, String dueAt, String dedupeKey) {
        NotificationPayload payload = NotificationPayload.of("행사", "code", "주최자");
        payload = switch (type) {
            case EVENT_REMINDER -> payload.withEventAt(dueAt);
            case WISHED_CAMPAIGN_OPENING_SOON -> payload.withOpenAt(dueAt);
            default -> payload;
        };
        return outboxRepository.save(new NotificationOutbox(CAMPAIGN_ID, type, payload, dedupeKey));
    }
}
