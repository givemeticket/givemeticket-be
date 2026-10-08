package kr.givemeticket.api.notification.ui;

import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.notification.application.NotificationService;
import kr.givemeticket.api.notification.ui.apiSpec.NotificationApiSpec;
import kr.givemeticket.api.notification.ui.dto.response.GetNotificationsResponse;
import kr.givemeticket.api.notification.ui.dto.response.GetUnreadCountResponse;
import kr.givemeticket.api.notification.ui.dto.response.ReadAllNotificationsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationController implements NotificationApiSpec {

    private final NotificationService notificationService;

    @Override
    @GetMapping("notifications")
    public ResponseEntity<GetNotificationsResponse> readNotifications(
            @LoginUserId Long userId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ResponseEntity.ok(GetNotificationsResponse.from(
                notificationService.getNotifications(userId, cursor, size)));
    }

    @Override
    @GetMapping("notifications/unread-count")
    public ResponseEntity<GetUnreadCountResponse> readUnreadCount(@LoginUserId Long userId) {
        return ResponseEntity.ok(new GetUnreadCountResponse(notificationService.countUnread(userId)));
    }

    @Override
    @PatchMapping("notifications/{notificationId}/read")
    public ResponseEntity<Void> readNotification(
            @LoginUserId Long userId,
            @PathVariable("notificationId") Long notificationId
    ) {
        notificationService.markRead(userId, notificationId);

        return ResponseEntity.noContent().build();
    }

    @Override
    @BusinessLogging("알림 모두 읽음")
    @PatchMapping("notifications/read-all")
    public ResponseEntity<ReadAllNotificationsResponse> readAllNotifications(@LoginUserId Long userId) {
        return ResponseEntity.ok(
                new ReadAllNotificationsResponse(notificationService.markAllRead(userId)));
    }
}
