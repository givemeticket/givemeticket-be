package kr.givemeticket.api.apply.domain;

import java.time.LocalDateTime;

/**
 * 큐를 타고 워커에게 건너가는 예매 생성 이벤트.
 *
 * <p>언제: 좌석 선점이 끝난 직후 만들어져 메인 큐에 실린다. 실패하면 재시도할 때마다
 * {@link #nextAttempt()} 로 사본이 만들어진다.
 *
 * @param applicationId DB 가 아니라 Redis 에서 미리 채번한 값
 * @param attempt       재시도 횟수. 0이 최초 시도. 백오프 대기와 DLQ 판정의 기준
 * @param occurredAt    좌석을 잡은 시각. 큐에서 머문 시간을 재는 기준점
 */
public record ReservationEvent(
        Long applicationId,
        Long campaignId,
        Long userId,
        int attempt,
        LocalDateTime occurredAt
) {

    public ReservationEvent {
        if (applicationId == null || campaignId == null || userId == null) {
            throw new IllegalArgumentException("applicationId, campaignId, userId 는 필수다");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt 은 필수다");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt 는 음수일 수 없다: " + attempt);
        }
    }

    /** 큐에 처음 들어가는 이벤트를 만든다. */
    public static ReservationEvent first(
            Long applicationId, Long campaignId, Long userId, LocalDateTime occurredAt) {
        return new ReservationEvent(applicationId, campaignId, userId, 0, occurredAt);
    }

    /** 재시도 횟수만 하나 올린 사본을 만든다. 원본은 그대로 둔다. */
    public ReservationEvent nextAttempt() {
        return new ReservationEvent(applicationId, campaignId, userId, attempt + 1, occurredAt);
    }
}
