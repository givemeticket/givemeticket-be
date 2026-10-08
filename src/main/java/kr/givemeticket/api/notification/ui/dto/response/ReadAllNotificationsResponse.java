package kr.givemeticket.api.notification.ui.dto.response;

/**
 * @param readCount 이번에 읽음으로 바뀐 알림 수. 이미 다 읽었으면 0
 */
public record ReadAllNotificationsResponse(int readCount) {
}
