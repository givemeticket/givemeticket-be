package kr.givemeticket.api.notification.ui.dto.response;

import java.time.Instant;
import java.util.List;
import kr.givemeticket.api.global.time.Utc;
import kr.givemeticket.api.notification.application.dto.response.NotificationPageResponse;
import kr.givemeticket.api.notification.application.dto.response.NotificationResponse;
import kr.givemeticket.api.notification.domain.NotificationPayload;
import kr.givemeticket.api.notification.domain.NotificationType;

/**
 * @param nextCursor 다음 페이지를 부를 때 cursor 로 넘긴다. 마지막 페이지면 null
 */
public record GetNotificationsResponse(List<Item> notifications, Long nextCursor) {

    /**
     * @param campaignTitle 알림이 만들어질 때의 행사 제목. 이후 제목이 바뀌어도 그대로다
     * @param shortCode     행사 상세로 이동할 때 쓴다
     * @param ownerNickname 알림이 만들어질 때의 주최자 닉네임. 이 필드가 생기기 전 알림이면 null
     * @param changes       CAMPAIGN_CHANGED 에서만 채워진다
     * @param eventAt       EVENT_REMINDER 에서만 채워진다. UTC
     * @param openAt        WISHED_CAMPAIGN_OPENING_SOON 에서만 채워진다. UTC
     * @param closedAt      CAMPAIGN_CLOSED 에서만 채워진다. UTC
     * @param createdAt     UTC
     */
    public record Item(
            Long id,
            Long campaignId,
            NotificationType type,
            String campaignTitle,
            String shortCode,
            String ownerNickname,
            List<NotificationPayload.Change> changes,
            Instant eventAt,
            Instant openAt,
            Instant closedAt,
            boolean read,
            Instant createdAt
    ) {

        private static Item from(NotificationResponse notification) {
            NotificationPayload payload = notification.payload();
            return new Item(
                    notification.id(),
                    notification.campaignId(),
                    notification.type(),
                    payload.campaignTitle(),
                    payload.shortCode(),
                    payload.ownerNickname(),
                    (payload.changes() == null) ? List.of() : payload.changes(),
                    instantOrNull(payload.eventAt()),
                    instantOrNull(payload.openAt()),
                    instantOrNull(payload.closedAt()),
                    notification.read(),
                    Utc.toInstant(notification.createdAt()));
        }
    }

    private static Instant instantOrNull(String utc) {
        return (utc == null) ? null : Instant.parse(utc);
    }

    public static GetNotificationsResponse from(NotificationPageResponse page) {
        return new GetNotificationsResponse(
                page.notifications().stream().map(Item::from).toList(),
                page.nextCursor());
    }
}
