package kr.givemeticket.api.notification.application;

import kr.givemeticket.api.apply.domain.ApplicationCancelledByOwnerEvent;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignChange;
import kr.givemeticket.api.campaign.domain.CampaignChangedEvent;
import kr.givemeticket.api.campaign.domain.CampaignDeletedEvent;
import kr.givemeticket.api.campaign.domain.CampaignOpenedEvent;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.global.time.Utc;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import kr.givemeticket.api.notification.domain.NotificationPayload;
import kr.givemeticket.api.notification.domain.NotificationRepository;
import kr.givemeticket.api.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 행사·신청 쪽에서 난 일을 알림으로 옮긴다. 행사·신청 모듈은 알림을 모르고 이벤트만 낸다.
 *
 * <p>오픈 알림을 뺀 나머지는 {@code @TransactionalEventListener} 가 아니라 {@code @EventListener} 다.
 * 발행한 쪽의 트랜잭션 안에서 그대로 실행되므로 원본 기록이 행사 수정과 함께 커밋되거나 함께
 * 롤백된다. 커밋 뒤에 따로 쓰면 그 사이에 서버가 죽었을 때 알림이 사라진다. 오픈 알림이 왜
 * 예외인지는 {@link #on(CampaignOpenedEvent)} 에 적었다.
 *
 * <p>여기서 실패하면 발행한 요청도 실패한다. 실패 원인은 DB 쓰기뿐인데, 그 상황이면
 * 행사 수정도 어차피 성공하기 어렵다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final CampaignRepository campaignRepository;
    private final OutboxEnqueuer outboxEnqueuer;

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

    /**
     * 오픈만 다른 경로를 탄다. <b>오픈이 커밋된 뒤</b> 따로 기록한다.
     *
     * <p>수정·삭제는 사용자의 요청이라 알림 기록이 실패하면 요청이 실패해도 된다. 오픈은 다르다.
     * 같은 트랜잭션에 두면 알림 INSERT 하나가 실패할 때 오픈이 롤백되고, 스케줄러가 1초마다 다시
     * 시도해도 같은 이유로 계속 실패해 티켓 판매가 열리지 않는다. 알림보다 오픈이 중요하다.
     * 대가로, 오픈 커밋과 이 기록 사이에 서버가 죽으면 그 오픈 알림은 사라진다.
     *
     * <p>같은 오픈에는 원본이 하나만 생긴다. 키에 오픈 시각이 들어 있어서, 오픈을 미뤘다가 다시
     * 열리면 새 원본이 생겨 한 번 더 알린다. 파드마다 도는 스케줄러가 같은 행사를 동시에 열어도
     * 유니크 제약이 하나만 남긴다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CampaignOpenedEvent event) {
        String dedupeKey = "opened:" + event.campaignId() + ":" + Utc.toInstant(event.openAt());
        try {
            outboxEnqueuer.enqueueOnce(new NotificationOutbox(
                    event.campaignId(),
                    NotificationType.WISHED_CAMPAIGN_OPENED,
                    NotificationPayload.of(event.title(), event.shortCode()),
                    dedupeKey));
        } catch (DataIntegrityViolationException e) {
            // 다른 파드가 같은 오픈을 한발 먼저 넣었다(유니크). 원하는 결과와 같다.
            log.debug("wished campaign opened notification already enqueued: campaignId={}",
                    event.campaignId());
        } catch (RuntimeException e) {
            // 어떤 실패든 이미 열린 행사를 되돌릴 이유는 아니다. type 컬럼의 CHECK 제약에 새 알림
            // 종류가 막힌 경우도 여기로 온다(MySQL 은 CHECK 위반을 무결성 위반으로 분류하지 않는다).
            // 사람이 제약을 지워야 하는 일이라 error 로 남긴다.
            log.error("wished campaign opened notification failed: campaignId={}", event.campaignId(), e);
        }
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
