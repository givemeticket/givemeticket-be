package kr.givemeticket.api.notification.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.IntSupplier;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import kr.givemeticket.api.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간이 지난 알림과 처리가 끝난 원본을 지운다. 파드마다 돌아도 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private static final Duration NOTIFICATION_RETENTION = Duration.ofDays(90);
    private static final Duration OUTBOX_RETENTION = Duration.ofDays(7);
    private static final int BATCH_SIZE = 1_000;

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;

    /** 서버 시각이 UTC 라 19시가 한국 시각 새벽 4시다. */
    @Scheduled(cron = "${notification.cleanup-cron:0 0 19 * * *}")
    public void cleanUp() {
        LocalDateTime now = LocalDateTime.now();

        int notifications = deleteInBatches(() -> notificationRepository.deleteCreatedBefore(
                now.minus(NOTIFICATION_RETENTION), BATCH_SIZE));
        int outboxes = deleteInBatches(() -> outboxRepository.deleteProcessedBefore(
                now.minus(OUTBOX_RETENTION), BATCH_SIZE));

        log.info("notification cleanup: notifications={}, outboxes={}", notifications, outboxes);
    }

    private static int deleteInBatches(IntSupplier deleteBatch) {
        int total = 0;
        int deleted;
        do {
            deleted = deleteBatch.getAsInt();
            total += deleted;
        } while (deleted == BATCH_SIZE);
        return total;
    }
}
