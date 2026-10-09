package kr.givemeticket.api.notification.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.global.time.Utc;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import kr.givemeticket.api.notification.domain.NotificationRepository;
import kr.givemeticket.api.notification.domain.NotificationType;
import kr.givemeticket.api.wish.domain.WishRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원본 하나를 신청자 수만큼의 알림으로 펼친다.
 *
 * <p>원본을 잠그는 것, 알림을 넣는 것, 처리 표시를 하는 것이 <b>한 트랜잭션</b>이다. 중간에 죽으면
 * 전부 롤백되고 원본은 다시 "처리 전"으로 남아 다음 차례에 처음부터 펼쳐진다. 절반만 들어간
 * 상태가 남지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFanOut {

    private static final Set<ApplicationStatus> CONFIRMED_OR_CANCELLED =
            Set.of(ApplicationStatus.CONFIRMED, ApplicationStatus.CANCELLED);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationRepository applicationRepository;
    private final CampaignRepository campaignRepository;
    private final WishRepository wishRepository;

    /**
     * @return 처리할 원본이 있었으면 true. false 면 지금은 더 할 일이 없다
     */
    @Transactional
    public boolean fanOutNext() {
        NotificationOutbox outbox = outboxRepository.claimNext().orElse(null);
        if (outbox == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (isStaleReminder(outbox, now)) {
            // 서버가 오래 내려가 있었던 경우다. 이미 지난 일에 "곧 ~합니다"를 보내지 않는다.
            log.info("stale reminder skipped: outboxId={}, campaignId={}",
                    outbox.getId(), outbox.getCampaignId());
            outbox.markProcessed(now);
            return true;
        }

        List<Long> recipients = recipientsOf(outbox);
        notificationRepository.saveAll(recipients.stream()
                .map(userId -> new Notification(
                        userId,
                        outbox.getCampaignId(),
                        outbox.getType(),
                        outbox.getPayload(),
                        outbox.fanOutKey()))
                .toList());
        outbox.markProcessed(now);

        log.info("notification fanned out: outboxId={}, type={}, campaignId={}, recipients={}",
                outbox.getId(), outbox.getType(), outbox.getCampaignId(), recipients.size());
        return true;
    }

    /**
     * 받는 사람은 <b>펼치는 시점</b>의 신청자다. 원본이 생긴 뒤 신청한 사람도 받는데, 그 사람은
     * 바뀐 내용을 보고 신청했으니 알림이 하나 더 와도 해가 없다.
     *
     * <p>주최자는 빠진다. 자기가 한 일을 자기에게 알릴 필요가 없다.
     */
    private List<Long> recipientsOf(NotificationOutbox outbox) {
        Long ownerId = campaignRepository.findById(outbox.getCampaignId())
                .map(Campaign::getOwnerId)
                .orElse(null);

        List<Long> candidates = switch (outbox.getType()) {
            case WISHED_CAMPAIGN_OPENED, WISHED_CAMPAIGN_OPENING_SOON ->
                    wishersNotYetApplied(outbox.getCampaignId());
            case CAMPAIGN_CHANGED -> applicantsAndWishers(outbox.getCampaignId());
            default -> applicantsOf(outbox).stream().map(Application::getUserId).toList();
        };

        return candidates.stream()
                .filter(userId -> !Objects.equals(userId, ownerId))
                .distinct()
                .toList();
    }

    /**
     * 변경 알림은 신청자와 찜한 사람 모두에게 간다. 오픈 시각이 바뀌면 오픈을 기다리는 찜한 사람이
     * 알아야 하고, 장소·일시는 신청자가 알아야 한다. 어느 항목이 바뀌었든 둘 다에게 보낸다 — 찜한
     * 사람에게 장소 변경이 가도 해가 없고, 항목별로 받는 사람을 나누면 규칙만 늘어난다.
     */
    private List<Long> applicantsAndWishers(Long campaignId) {
        List<Long> recipients = new ArrayList<>(applicationRepository
                .findAllByCampaignIdAndStatusIn(campaignId, ApplicationStatus.active()).stream()
                .map(Application::getUserId)
                .toList());
        recipients.addAll(wishRepository.findUserIdsByCampaignId(campaignId));
        return recipients;
    }

    /**
     * 오픈 알림은 찜한 사람에게 간다. 이미 자리를 잡은 사람은 뺀다 — 오픈을 미뤘다가 다시 열 때는
     * 먼저 신청해 둔 사람이 있을 수 있는데, 그 사람에게 "열렸습니다"는 필요 없다.
     */
    private List<Long> wishersNotYetApplied(Long campaignId) {
        Set<Long> applied = applicationRepository
                .findAllByCampaignIdAndStatusIn(campaignId, ApplicationStatus.active()).stream()
                .map(Application::getUserId)
                .collect(Collectors.toSet());

        return wishRepository.findUserIdsByCampaignId(campaignId).stream()
                .filter(userId -> !applied.contains(userId))
                .toList();
    }

    /**
     * 삭제는 다르게 고른다. 삭제 표시가 커밋된 뒤에 신청 취소가 건별로 돌아서, 이 워커가 먼저 돌면
     * 아직 확정 상태인 신청과 이미 취소된 신청이 섞여 있다. 둘 다 받아야 한다.
     * 그 사이 본인이 직접 취소한 신청(사유 없음)만 빠진다.
     */
    private List<Application> applicantsOf(NotificationOutbox outbox) {
        if (outbox.getType() == NotificationType.CAMPAIGN_DELETED) {
            return applicationRepository
                    .findAllByCampaignIdAndStatusIn(outbox.getCampaignId(), CONFIRMED_OR_CANCELLED)
                    .stream()
                    .filter(application -> application.isActive()
                            || application.getFailureReason() == FailureReason.CAMPAIGN_DELETED)
                    .toList();
        }
        return applicationRepository.findAllByCampaignIdAndStatusIn(
                outbox.getCampaignId(), ApplicationStatus.active());
    }

    /**
     * 시각을 앞두고 보내는 알림인데 그 시각이 이미 지났다. 임박 알림은 행사 시작, 오픈 임박 알림은
     * 오픈이 기준이다. 오픈이 지났으면 오픈 알림이 따로 간다.
     */
    private static boolean isStaleReminder(NotificationOutbox outbox, LocalDateTime now) {
        String dueAt = switch (outbox.getType()) {
            case EVENT_REMINDER -> outbox.getPayload().eventAt();
            case WISHED_CAMPAIGN_OPENING_SOON -> outbox.getPayload().openAt();
            default -> null;
        };
        return dueAt != null && !Instant.parse(dueAt).isAfter(Utc.toInstant(now));
    }
}
