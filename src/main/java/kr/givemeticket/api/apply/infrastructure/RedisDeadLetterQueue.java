package kr.givemeticket.api.apply.infrastructure;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.givemeticket.api.apply.domain.DeadLetter;
import kr.givemeticket.api.apply.domain.DeadLetterQueue;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * DLQ 의 Redis Stream 구현. 메인 큐와 같은 필드 형식이라 원문을 그대로 읽을 수 있고
 * 되돌릴 때 변환이 필요 없다.
 *
 * <p>언제: 워커가 격리할 때 넣고, 어드민 API 가 꺼내 보거나 되돌린다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisDeadLetterQueue implements DeadLetterQueue {

    static final String FIELD_REASON = "dlqReason";
    static final String FIELD_ISOLATED_AT = "dlqIsolatedAt";
    static final String FIELD_SOURCE_MESSAGE_ID = "dlqSourceMessageId";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<String> reservationPublishScript;
    private final ReservationQueueProperties properties;

    @Override
    public void isolate(ReservationEvent event, String reason) {
        Map<String, String> fields = new LinkedHashMap<>(ReservationEventCodec.encode(event));
        append(fields, reason, null);
    }

    @Override
    public void isolateRaw(String messageId, Map<String, String> fields, String reason) {
        append(new LinkedHashMap<>(fields), reason, messageId);
    }

    /** 격리 표시를 붙여 DLQ 스트림에 넣는다. */
    private void append(Map<String, String> fields, String reason, String sourceMessageId) {
        fields.put(FIELD_REASON, reason);
        fields.put(FIELD_ISOLATED_AT, LocalDateTime.now().toString());
        if (sourceMessageId != null) {
            fields.put(FIELD_SOURCE_MESSAGE_ID, sourceMessageId);
        }

        RecordId id = stringRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(fields).withStreamKey(properties.dlqStreamKey()));

        log.error("reservation isolated to dlq: dlqId={}, reason={}, fields={}",
                (id == null) ? null : id.getValue(), reason, fields);
    }

    @Override
    public List<DeadLetter> peek(int limit) {
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .range(properties.dlqStreamKey(), Range.unbounded(), Limit.limit().count(limit));
        if (records == null) {
            return List.of();
        }

        List<DeadLetter> letters = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            Map<String, String> fields = new LinkedHashMap<>();
            record.getValue().forEach((k, v) -> fields.put((String) k, (String) v));
            letters.add(new DeadLetter(
                    record.getId().getValue(), fields, fields.get(FIELD_REASON)));
        }
        return letters;
    }

    /**
     * 격리 표시를 걷어내고 메인 큐로 되돌린 뒤 DLQ 에서 지운다.
     *
     * <p><b>넣는 것이 먼저다.</b> 지우고 넣다가 죽으면 그 예매는 아무 데도 없다.
     */
    @Override
    public boolean requeue(String deadLetterId) {
        List<DeadLetter> found = peekById(deadLetterId);
        if (found.isEmpty()) {
            return false;
        }

        Map<String, String> fields = new LinkedHashMap<>(found.get(0).fields());
        fields.remove(FIELD_REASON);
        fields.remove(FIELD_ISOLATED_AT);
        fields.remove(FIELD_SOURCE_MESSAGE_ID);

        try {
            // 원인을 고쳐 다시 넣는 것이므로 재시도 횟수를 초기화한다.
            fields.put(ReservationEventCodec.FIELD_ATTEMPT, "0");
            ReservationEventCodec.decode(fields);
        } catch (RuntimeException e) {
            log.error("dlq requeue rejected, message is not a reservation event: dlqId={}",
                    deadLetterId, e);
            return false;
        }

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(properties.maxLength()));
        fields.forEach((field, value) -> {
            args.add(field);
            args.add(value);
        });
        stringRedisTemplate.execute(
                reservationPublishScript, List.of(properties.streamKey()), args.toArray());

        stringRedisTemplate.opsForStream().delete(properties.dlqStreamKey(), deadLetterId);
        log.info("dlq message requeued: dlqId={}, applicationId={}",
                deadLetterId, fields.get(ReservationEventCodec.FIELD_APPLICATION_ID));
        return true;
    }

    @Override
    public long size() {
        Long size = stringRedisTemplate.opsForStream().size(properties.dlqStreamKey());
        return (size == null) ? 0L : size;
    }

    /** id 로 한 건만 읽는다. */
    private List<DeadLetter> peekById(String deadLetterId) {
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .range(properties.dlqStreamKey(),
                        Range.closed(deadLetterId, deadLetterId));
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        records.get(0).getValue().forEach((k, v) -> fields.put((String) k, (String) v));
        return List.of(new DeadLetter(deadLetterId, fields, fields.get(FIELD_REASON)));
    }
}
