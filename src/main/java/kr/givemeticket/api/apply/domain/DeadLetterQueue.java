package kr.givemeticket.api.apply.domain;

import java.util.List;
import java.util.Map;

/**
 * 더 손쓸 수 없는 메시지를 메인 흐름에서 떼어내 보관한다. 구현은 Redis Stream 이다.
 *
 * <p>여기 들어온 메시지는 버려진 것이 아니라 <b>사람에게 넘어간 것</b>이다.
 *
 * <p>언제: 재시도 한도를 넘겼거나 해석조차 되지 않았을 때 워커가 넣고,
 * 어드민 API 가 꺼내 본다.
 */
public interface DeadLetterQueue {

    /** 해석된 예매를 격리한다. {@code reason} 은 원인 추적의 첫 단서다. */
    void isolate(ReservationEvent event, String reason);

    /** 해석되지 않은 메시지를 원문 그대로 격리한다. */
    void isolateRaw(String messageId, Map<String, String> fields, String reason);

    /** 오래된 것부터 최대 {@code limit} 건을 들여다본다. */
    List<DeadLetter> peek(int limit);

    /** 메인 큐로 되돌리고 DLQ 에서 지운다. 이미 없으면 false. */
    boolean requeue(String deadLetterId);

    /** 격리된 메시지 수. */
    long size();
}
