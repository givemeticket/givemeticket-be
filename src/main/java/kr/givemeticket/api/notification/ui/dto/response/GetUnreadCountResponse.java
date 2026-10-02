package kr.givemeticket.api.notification.ui.dto.response;

/**
 * @param unreadCount 최대 100 까지만 센다. 100 이면 "99+" 로 그리면 된다
 */
public record GetUnreadCountResponse(int unreadCount) {
}
