package kr.givemeticket.api.apply.infrastructure;

import java.util.Map;
import kr.givemeticket.api.apply.application.ReservationWorker;
import kr.givemeticket.api.apply.application.ReservationWorker.Disposition;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * 메인 큐에서 온 메시지를 해석해 워커에게 넘기고, 워커가 그러라고 할 때만 ack 한다.
 *
 * <p>Redis 를 아는 얇은 껍데기다. 무엇을 할지는 워커가 정한다.
 *
 * <p>언제: 리스너 컨테이너가 메시지를 가져올 때마다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final StringRedisTemplate stringRedisTemplate;
    private final ReservationWorker reservationWorker;
    private final ReservationQueueProperties properties;

    /** 메시지 하나를 처리한다. */
    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String messageId = record.getId().getValue();
        Map<String, String> fields = record.getValue();

        Disposition disposition;
        try {
            disposition = reservationWorker.handle(
                    messageId, ReservationEventCodec.decode(fields));
        } catch (ReservationEventDecodeException e) {
            disposition = reservationWorker.handleUndecodable(messageId, fields, e);
        }

        if (disposition == Disposition.ACKNOWLEDGE) {
            acknowledge(record);
        }
    }

    /** 처리 중 목록에서 이 메시지를 뺀다. */
    private void acknowledge(MapRecord<String, String, String> record) {
        stringRedisTemplate.opsForStream()
                .acknowledge(properties.group(), record);
    }
}
