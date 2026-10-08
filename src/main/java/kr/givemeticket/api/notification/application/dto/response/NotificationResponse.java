package kr.givemeticket.api.notification.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationPayload;
import kr.givemeticket.api.notification.domain.NotificationType;

public record NotificationResponse(
        Long id,
        Long campaignId,
        NotificationType type,
        NotificationPayload payload,
        boolean read,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getCampaignId(),
                notification.getType(),
                notification.getPayload(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
