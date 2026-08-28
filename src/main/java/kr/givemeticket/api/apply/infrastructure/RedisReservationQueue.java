package kr.givemeticket.api.apply.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import kr.givemeticket.api.apply.domain.ReservationQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * 메인 큐의 Redis Stream 구현.
 *
 * <p>언제: 신청이 큐에 넣을 때, 그리고 기동 시 소비 그룹을 준비할 때.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisReservationQueue implements ReservationQueue {

    /** XGROUP CREATE 가 이미 있는 그룹에 대해 돌려주는 에러. 기동을 막을 이유가 아니다. */
    private static final String GROUP_ALREADY_EXISTS = "BUSYGROUP";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<String> reservationPublishScript;
    private final ReservationQueueProperties properties;

    @Override
    public String publish(ReservationEvent event) {
        Map<String, String> fields = ReservationEventCodec.encode(event);

        // ARGV[1] 은 MAXLEN, 그 뒤로 필드-값이 번갈아 이어진다
        List<String> args = new ArrayList<>(fields.size() * 2 + 1);
        args.add(String.valueOf(properties.maxLength()));
        fields.forEach((field, value) -> {
            args.add(field);
            args.add(value);
        });

        String messageId = stringRedisTemplate.execute(
                reservationPublishScript, List.of(properties.streamKey()), args.toArray());

        log.debug("reservation event published: applicationId={}, attempt={}, messageId={}",
                event.applicationId(), event.attempt(), messageId);
        return messageId;
    }

    /**
     * 소비 그룹을 만든다. 스트림이 없으면 함께 만들고, 이미 있으면 아무것도 하지 않는다.
     *
     * @return 이번 호출로 실제 만들어졌으면 true
     */
    public boolean ensureConsumerGroup() {
        try {
            stringRedisTemplate.opsForStream()
                    .createGroup(properties.streamKey(), ReadOffset.from("0"), properties.group());
            log.info("reservation consumer group created: stream={}, group={}",
                    properties.streamKey(), properties.group());
            return true;
        } catch (RuntimeException e) {
            if (alreadyExists(e)) {
                log.debug("reservation consumer group already exists: stream={}, group={}",
                        properties.streamKey(), properties.group());
                return false;
            }
            throw e;
        }
    }

    /** 이미 있는 그룹이라 난 에러인지. */
    private boolean alreadyExists(RuntimeException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(GROUP_ALREADY_EXISTS)) {
                return true;
            }
        }
        return false;
    }
}
