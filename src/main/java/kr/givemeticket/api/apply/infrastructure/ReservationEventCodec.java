package kr.givemeticket.api.apply.infrastructure;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.givemeticket.api.apply.domain.ReservationEvent;

/**
 * {@link ReservationEvent} 와 Stream 엔트리(필드-값 쌍) 사이의 변환.
 *
 * <p>JSON 대신 평평한 문자열 맵을 쓴다. Stream 구조에 그대로 얹히고, 장애 때
 * {@code XRANGE} 로 꺼낸 원문을 사람이 바로 읽을 수 있다.
 */
public final class ReservationEventCodec {

    static final String FIELD_APPLICATION_ID = "applicationId";
    static final String FIELD_CAMPAIGN_ID = "campaignId";
    static final String FIELD_USER_ID = "userId";
    static final String FIELD_ATTEMPT = "attempt";
    static final String FIELD_OCCURRED_AT = "occurredAt";

    /** 지연 큐 멤버를 이어 붙이는 구분자. Lua 가 이 문자로 잘라 XADD 인자로 넘긴다. */
    static final String FLAT_SEPARATOR = "|";

    private ReservationEventCodec() {
    }

    /** 이벤트를 Stream 필드-값 맵으로 바꾼다. */
    public static Map<String, String> encode(ReservationEvent event) {
        // 사람이 XRANGE 로 읽을 때 필드 순서가 뒤섞이지 않게 한다.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_APPLICATION_ID, String.valueOf(event.applicationId()));
        fields.put(FIELD_CAMPAIGN_ID, String.valueOf(event.campaignId()));
        fields.put(FIELD_USER_ID, String.valueOf(event.userId()));
        fields.put(FIELD_ATTEMPT, String.valueOf(event.attempt()));
        fields.put(FIELD_OCCURRED_AT, event.occurredAt().toString());
        return fields;
    }

    /**
     * Stream 필드-값 맵을 이벤트로 되돌린다.
     *
     * @throws ReservationEventDecodeException 필드가 빠졌거나 형식이 깨진 경우.
     *         재시도해도 결과가 같으므로 호출자는 곧바로 DLQ 로 보내야 한다
     */
    public static ReservationEvent decode(Map<String, String> fields) {
        try {
            return new ReservationEvent(
                    Long.parseLong(required(fields, FIELD_APPLICATION_ID)),
                    Long.parseLong(required(fields, FIELD_CAMPAIGN_ID)),
                    Long.parseLong(required(fields, FIELD_USER_ID)),
                    Integer.parseInt(required(fields, FIELD_ATTEMPT)),
                    LocalDateTime.parse(required(fields, FIELD_OCCURRED_AT))
            );
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new ReservationEventDecodeException(fields, e);
        }
    }

    /** 지연 큐에 넣을 형태로 이어 붙인다. {@code field|value|field|value|...} */
    public static String flatten(ReservationEvent event) {
        List<String> tokens = new ArrayList<>();
        encode(event).forEach((field, value) -> {
            if (value.contains(FLAT_SEPARATOR)) {
                throw new IllegalArgumentException(
                        "지연 큐 멤버에 구분자가 섞였다: " + field + "=" + value);
            }
            tokens.add(field);
            tokens.add(value);
        });
        return String.join(FLAT_SEPARATOR, tokens);
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null) {
            throw new IllegalArgumentException("필드가 없다: " + name);
        }
        return value;
    }
}
