package kr.givemeticket.api.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 쌓인 원본을 주기적으로 펼친다. 파드마다 돌아도 된다 — 원본을 SKIP LOCKED 로 잡아서
 * 같은 원본을 두 파드가 동시에 펼치지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFanOutWorker {

    /** 한 번 깨어났을 때 처리할 원본 수의 상한. 밀린 게 많아도 스케줄러 스레드를 오래 붙잡지 않는다. */
    private static final int MAX_PER_RUN = 50;

    private final NotificationFanOut notificationFanOut;

    /**
     * 실패하면 이번 차례를 접는다. 원본은 롤백되어 남아 있고 다음 차례에 다시 시도된다.
     * 같은 원본이 계속 실패하면 뒤의 원본도 막히므로 로그를 error 로 남긴다.
     */
    @Scheduled(fixedDelayString = "${notification.fan-out-delay-ms:1000}")
    public void run() {
        for (int i = 0; i < MAX_PER_RUN; i++) {
            try {
                if (!notificationFanOut.fanOutNext()) {
                    return;
                }
            } catch (RuntimeException e) {
                log.error("notification fan-out failed", e);
                return;
            }
        }
    }
}
