package kr.givemeticket.api.notification.application;

import kr.givemeticket.api.apply.domain.ApplicationCancelledByOwnerEvent;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignChange;
import kr.givemeticket.api.campaign.domain.CampaignChangedEvent;
import kr.givemeticket.api.campaign.domain.CampaignDeletedEvent;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import kr.givemeticket.api.notification.domain.NotificationPayload;
import kr.givemeticket.api.notification.domain.NotificationRepository;
import kr.givemeticket.api.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 행사·신청 쪽에서 난 일을 알림으로 옮긴다. 행사·신청 모듈은 알림을 모르고 이벤트만 낸다.
 *
 * <p>{@code @TransactionalEventListener} 가 아니라 {@code @EventListener} 다. 발행한 쪽의
 * 트랜잭션 안에서 그대로 실행되므로 원본 기록이 행사 수정과 함께 커밋되거나 함께 롤백된다.
 * 커밋 뒤에 따로 쓰면 그 사이에 서버가 죽었을 때 알림이 사라진다.
 *
 * <p>여기서 실패하면 발행한 요청도 실패한다. 실패 원인은 DB 쓰기뿐인데, 그 상황이면
 * 행사 수정도 어차피 성공하기 어렵다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final CampaignRepository campaignRepository;

    /** 신청자가 여럿이라 원본만 남기고 펼치는 일은 워커에 넘긴다. */
    @EventListener
    public void on(CampaignChangedEvent event) {
        NotificationPayload payload = new NotificationPayload(
                event.title(),
                event.shortCode(),
                event.changes().stream().map(NotificationEventListener::toChange).toList(),
                null);

        outboxRepository.save(new NotificationOutbox(
                event.campaignId(), NotificationType.CAMPAIGN_CHANGED, payload, null));
    }

    @EventListener
    public void on(CampaignDeletedEvent event) {
        outboxRepository.save(new NotificationOutbox(
                event.campaignId(),
                NotificationType.CAMPAIGN_DELETED,
                NotificationPayload.of(event.title(), event.shortCode()),
                null));
    }

    /** 받는 사람이 한 명이라 원본을 거치지 않고 곧바로 알림함에 넣는다. */
    @EventListener
    public void on(ApplicationCancelledByOwnerEvent event) {
        Campaign campaign = campaignRepository.findById(event.campaignId())
                .orElseThrow(() -> new IllegalStateException(
                        "campaign not found for cancelled application: " + event.campaignId()));

        notificationRepository.save(new Notification(
                event.userId(),
                event.campaignId(),
                NotificationType.APPLICATION_CANCELLED,
                NotificationPayload.of(campaign.getTitle(), campaign.getShortCode()),
                null));
    }

    private static NotificationPayload.Change toChange(CampaignChange change) {
        return new NotificationPayload.Change(
                change.field().name(), change.before(), change.after());
    }
}
