package kr.givemeticket.api.apply.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파이프라인이 쓰는 Redis 키와 소비 그룹 이름을 한곳에 모은다.
 *
 * @param streamKey     메인 큐 (Stream)
 * @param group         워커들이 경쟁 소비하는 Consumer Group
 * @param delayQueueKey 지연 재시도 큐 (ZSET). score 가 다음 재시도 시각
 * @param dlqStreamKey  격리된 메시지를 담는 스트림
 * @param maxLength     메인 큐 보관 상한. XACK 은 스트림을 비우지 않으므로 이게 없으면
 *                      성공한 메시지까지 계속 쌓인다
 */
@ConfigurationProperties(prefix = "reservation.queue")
public record ReservationQueueProperties(
        String streamKey,
        String group,
        String delayQueueKey,
        String dlqStreamKey,
        long maxLength
) {

    private static final String DEFAULT_STREAM_KEY = "reservation:stream";
    private static final String DEFAULT_GROUP = "reservation-workers";
    private static final String DEFAULT_DELAY_QUEUE_KEY = "reservation:retry";
    private static final String DEFAULT_DLQ_STREAM_KEY = "reservation:dlq";
    private static final long DEFAULT_MAX_LENGTH = 100_000L;

    public ReservationQueueProperties {
        streamKey = blankTo(streamKey, DEFAULT_STREAM_KEY);
        group = blankTo(group, DEFAULT_GROUP);
        delayQueueKey = blankTo(delayQueueKey, DEFAULT_DELAY_QUEUE_KEY);
        dlqStreamKey = blankTo(dlqStreamKey, DEFAULT_DLQ_STREAM_KEY);
        maxLength = (maxLength <= 0) ? DEFAULT_MAX_LENGTH : maxLength;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
