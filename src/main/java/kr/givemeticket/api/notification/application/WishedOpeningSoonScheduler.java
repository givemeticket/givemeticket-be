package kr.givemeticket.api.notification.application;

import java.time.Duration;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.global.time.Utc;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import kr.givemeticket.api.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오픈이 10분 안으로 다가온 행사를 찜한 사람에게 알린다. 선착순이라 열린 뒤에 받는 오픈 알림만으로는
 * 늦을 수 있다.
 *
 * <p>임박 알림({@link EventReminderScheduler})과 같은 방식이다. "10분 안쪽인데 아직 안 보낸 것"을 찾고,
 * dedupe_key 에 오픈 시각을 넣어 오픈을 미루면 새 시각 기준으로 한 번 더 나간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WishedOpeningSoonScheduler {

    private static final Duration LEAD_TIME = Duration.ofMinutes(10);

    private final CampaignRepository campaignRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationPayloads payloads;

    /** 10분 전 알림이 1분 넘게 늦지 않도록 30초마다 본다. */
    @Scheduled(fixedDelayString = "${notification.opening-soon-delay-ms:30000}")
    public void enqueueOpeningSoon() {
        LocalDateTime now = LocalDateTime.now();

        for (Campaign campaign : campaignRepository.findAllScheduledByOpenAtBetween(now, now.plus(LEAD_TIME))) {
            String openAt = Utc.toInstant(campaign.getOpenAt()).toString();
            String dedupeKey = "opening-soon:" + campaign.getId() + ":" + openAt;
            if (outboxRepository.existsByDedupeKey(dedupeKey)) {
                continue;
            }

            try {
                outboxRepository.save(new NotificationOutbox(
                        campaign.getId(),
                        NotificationType.WISHED_CAMPAIGN_OPENING_SOON,
                        payloads.of(campaign).withOpenAt(openAt),
                        dedupeKey));
                log.info("opening soon enqueued: campaignId={}, openAt={}", campaign.getId(), openAt);
            } catch (DataIntegrityViolationException e) {
                // 다른 파드가 한발 먼저 넣었다. 원하는 결과와 같다.
                log.debug("opening soon already enqueued: campaignId={}", campaign.getId());
            }
        }
    }
}
