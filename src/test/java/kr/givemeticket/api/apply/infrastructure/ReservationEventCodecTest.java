package kr.givemeticket.api.apply.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationEventCodecTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 10, 0);

    @Test
    @DisplayName("왕복해도 그대로다")
    void roundTrip() {
        ReservationEvent event = ReservationEvent.first(100L, 1L, 7L, NOW);

        assertThat(ReservationEventCodec.decode(ReservationEventCodec.encode(event)))
                .isEqualTo(event);
    }

    @Test
    @DisplayName("재시도한 이벤트도 횟수를 잃지 않는다")
    void roundTripRetried() {
        ReservationEvent event = ReservationEvent.first(100L, 1L, 7L, NOW)
                .nextAttempt()
                .nextAttempt();

        assertThat(ReservationEventCodec.decode(ReservationEventCodec.encode(event)).attempt())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("XRANGE 로 사람이 읽도록 평평한 문자열로만 싣는다")
    void encodesFlatStrings() {
        Map<String, String> fields =
                ReservationEventCodec.encode(ReservationEvent.first(100L, 1L, 7L, NOW));

        assertThat(fields).containsExactly(
                Map.entry(ReservationEventCodec.FIELD_APPLICATION_ID, "100"),
                Map.entry(ReservationEventCodec.FIELD_CAMPAIGN_ID, "1"),
                Map.entry(ReservationEventCodec.FIELD_USER_ID, "7"),
                Map.entry(ReservationEventCodec.FIELD_ATTEMPT, "0"),
                Map.entry(ReservationEventCodec.FIELD_OCCURRED_AT, "2026-08-28T10:00"));
    }

    @Test
    @DisplayName("필드가 빠진 메시지는 해석하지 않고 던진다 — 재시도해도 같은 결과다")
    void rejectsMissingField() {
        Map<String, String> fields = new HashMap<>(
                ReservationEventCodec.encode(ReservationEvent.first(100L, 1L, 7L, NOW)));
        fields.remove(ReservationEventCodec.FIELD_CAMPAIGN_ID);

        assertThatThrownBy(() -> ReservationEventCodec.decode(fields))
                .isInstanceOf(ReservationEventDecodeException.class);
    }

    @Test
    @DisplayName("형식이 깨진 값도 같은 예외로 모은다")
    void rejectsMalformedValue() {
        Map<String, String> fields = new HashMap<>(
                ReservationEventCodec.encode(ReservationEvent.first(100L, 1L, 7L, NOW)));
        fields.put(ReservationEventCodec.FIELD_OCCURRED_AT, "어제쯤");

        assertThatThrownBy(() -> ReservationEventCodec.decode(fields))
                .isInstanceOf(ReservationEventDecodeException.class);
    }
}
