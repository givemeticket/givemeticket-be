package kr.givemeticket.api.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.givemeticket.api.notification.application.dto.response.NotificationPageResponse;
import kr.givemeticket.api.notification.application.dto.response.NotificationResponse;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationPayload;
import kr.givemeticket.api.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 알림함은 토큰의 userId 로 꺼내기만 한다. 고정할 것은 셋이다 — 내 것만 나오는가,
 * 커서로 끊어 읽을 수 있는가, 남의 알림을 건드릴 수 없는가.
 */
class NotificationServiceTest {

    private static final Long ME = 7L;
    private static final Long OTHER = 8L;

    private final FakeNotificationRepository repository = new FakeNotificationRepository();
    private final NotificationService service = new NotificationService(repository);

    @Test
    @DisplayName("내 알림만 최신순으로 나온다")
    void listsOnlyMineLatestFirst() {
        Notification first = given(ME);
        given(OTHER);
        Notification second = given(ME);

        NotificationPageResponse page = service.getNotifications(ME, null, null);

        assertThat(page.notifications()).extracting(NotificationResponse::id)
                .containsExactly(second.getId(), first.getId());
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("다음 페이지가 있으면 커서가 나오고, 그 커서로 이어서 읽는다")
    void pagesWithCursor() {
        for (int i = 0; i < 5; i++) {
            given(ME);
        }

        NotificationPageResponse first = service.getNotifications(ME, null, 3);
        NotificationPageResponse second = service.getNotifications(ME, first.nextCursor(), 3);

        assertThat(first.notifications()).hasSize(3);
        assertThat(first.nextCursor()).isEqualTo(first.notifications().get(2).id());
        assertThat(second.notifications()).hasSize(2);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("페이지 크기가 범위를 벗어나면 400 이다")
    void rejectsInvalidSize() {
        assertThatThrownBy(() -> service.getNotifications(ME, null, 0))
                .isInstanceOf(NotificationApplicationException.class);
        assertThatThrownBy(() -> service.getNotifications(ME, null, NotificationService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(NotificationApplicationException.class);
    }

    @Test
    @DisplayName("읽으면 안 읽은 수가 줄고, 다시 읽어도 처음 읽은 시각이 남는다")
    void marksReadIdempotently() {
        Notification notification = given(ME);
        given(ME);

        service.markRead(ME, notification.getId());
        LocalDateTime firstReadAt = notification.getReadAt();
        service.markRead(ME, notification.getId());

        assertThat(service.countUnread(ME)).isEqualTo(1);
        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("남의 알림은 읽음 처리할 수 없다 — 없는 것과 같은 404 다")
    void rejectsOthersNotification() {
        Notification others = given(OTHER);

        assertThatThrownBy(() -> service.markRead(ME, others.getId()))
                .isInstanceOf(NotificationApplicationException.class)
                .hasMessageContaining("찾을 수 없");
        assertThat(others.isRead()).isFalse();
    }

    @Test
    @DisplayName("모두 읽음은 내 알림만 바꾼다")
    void marksAllReadOnlyMine() {
        given(ME);
        given(ME);
        given(OTHER);

        assertThat(service.markAllRead(ME)).isEqualTo(2);
        assertThat(service.countUnread(ME)).isZero();
        assertThat(service.countUnread(OTHER)).isEqualTo(1);
    }

    @Test
    @DisplayName("안 읽은 수는 상한까지만 센다")
    void capsUnreadCount() {
        for (int i = 0; i < NotificationService.UNREAD_COUNT_CAP + 5; i++) {
            given(ME);
        }

        assertThat(service.countUnread(ME)).isEqualTo(NotificationService.UNREAD_COUNT_CAP);
    }

    private Notification given(Long userId) {
        return repository.save(new Notification(
                userId, 1L, NotificationType.CAMPAIGN_CHANGED,
                NotificationPayload.of("행사", "code"), null));
    }
}
