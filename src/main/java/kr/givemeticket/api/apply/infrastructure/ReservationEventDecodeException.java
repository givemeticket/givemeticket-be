package kr.givemeticket.api.apply.infrastructure;

import java.util.Map;

/**
 * 큐에서 꺼낸 메시지를 해석할 수 없다. 재시도로 풀리지 않으므로 곧바로 격리 대상이다.
 */
public class ReservationEventDecodeException extends RuntimeException {

    public ReservationEventDecodeException(Map<String, String> fields, Throwable cause) {
        super("예매 이벤트를 해석할 수 없다: " + fields, cause);
    }
}
