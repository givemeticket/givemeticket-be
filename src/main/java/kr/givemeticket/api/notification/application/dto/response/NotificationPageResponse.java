package kr.givemeticket.api.notification.application.dto.response;

import java.util.List;

/**
 * @param nextCursor 다음 페이지를 부를 때 넘길 값. 더 없으면 null
 */
public record NotificationPageResponse(List<NotificationResponse> notifications, Long nextCursor) {
}
