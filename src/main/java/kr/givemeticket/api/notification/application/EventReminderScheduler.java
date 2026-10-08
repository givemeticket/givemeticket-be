package kr.givemeticket.api.notification.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.global.time.Utc;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import kr.givemeticket.api.notification.domain.NotificationPayload;
import kr.givemeticket.api.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시작이 하루 안으로 다가온 행사의 신청자에게 임박 알림을 보낸다.
 *
 * <p>"정확히 24시간 전"이 아니라 "24시간 안쪽인데 아직 안 보낸 것"을 찾는다. 서버가 내려가 있던
 * 동안 그 순간을 지나쳤어도 올라오자마자 보낸다.
 *
 * <p>보냈는지는 원본의 dedupe_key 로 판단한다. 키에 시작 일시가 들어 있어서, 주최자가 일시를
 * 바꾸면 키가 달라져 새 일시 기준으로 한 번 더 나가고, 안 바꾸면 몇 번을 돌아도 한 번이다.
 * 같은 키는 유니크 제약이 하나만 남기므로 파드가 여럿이어도 겹치지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private static final Duration LEAD_TIME = Duration.ofHours(24);

    private final CampaignRepository campaignRepository;
    private final NotificationOutboxRepository outboxRepository;

    @Scheduled(fixedDelayString = "${notification.reminder-delay-ms:60000}")
    public void enqueueReminders() {
        LocalDateTime now = LocalDateTime.now();

        for (Campaign campaign : campaignRepository.findAllLiveByEventAtBetween(now, now.plus(LEAD_TIME))) {
            String eventAt = Utc.toInstant(campaign.getDetail().getEventAt()).toString();
            String dedupeKey = "reminder:D1:" + campaign.getId() + ":" + eventAt;
            if (outboxRepository.existsByDedupeKey(dedupeKey)) {
                continue;
            }

            try {
                outboxRepository.save(new NotificationOutbox(
                        campaign.getId(),
                        NotificationType.EVENT_REMINDER,
                        new NotificationPayload(
                                campaign.getTitle(), campaign.getShortCode(), List.of(), eventAt),
                        dedupeKey));
                log.info("event reminder enqueued: campaignId={}, eventAt={}", campaign.getId(), eventAt);
            } catch (DataIntegrityViolationException e) {
                // 다른 파드가 한발 먼저 넣었다. 원하는 결과와 같다.
                log.debug("event reminder already enqueued: campaignId={}", campaign.getId());
            }
        }
    }
}
