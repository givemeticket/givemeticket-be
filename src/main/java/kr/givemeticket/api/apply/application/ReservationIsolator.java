package kr.givemeticket.api.apply.application;

import java.util.Map;
import kr.givemeticket.api.apply.domain.DeadLetterQueue;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import kr.givemeticket.api.global.notification.OperatorNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 더 손쓸 수 없는 메시지를 DLQ 로 격리하고 사람에게 알린다.
 *
 * <p><b>재고는 되돌리지 않는다.</b> 사용자는 이미 201 을 받았다. 자리를 풀면 그 좌석이
 * 남에게 팔리고, 재처리 때 원래 고객이 밀려난다. 대가로 사람이 처리할 때까지 그 좌석은
 * 잠긴다 — DLQ 크기를 지켜봐야 하는 이유다.
 *
 * <p>언제: 재시도 한도를 넘겼거나 메시지를 해석할 수 없을 때 워커가 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationIsolator {

    private final DeadLetterQueue deadLetterQueue;
    private final OperatorNotifier operatorNotifier;

    /** 해석된 예매를 격리하고, 자리를 잡고 있다는 사실까지 알린다. */
    public void isolate(ReservationEvent event, String reason) {
        deadLetterQueue.isolate(event, reason);

        operatorNotifier.notifyFailure(
                "예매 저장 실패 — DLQ 격리 (재고 점유 중)",
                """
                applicationId : %d
                campaignId    : %d
                userId        : %d
                attempt       : %d
                reason        : %s

                이 예매의 자리는 그대로 잡아 두었습니다. 사용자는 이미 신청 성공 응답을
                받았기 때문입니다. 원인을 고친 뒤 재인입하면 그 자리에 그대로 확정됩니다.
                처리하지 않으면 이 좌석은 계속 잠겨 있습니다."""
                        .formatted(event.applicationId(), event.campaignId(), event.userId(),
                                event.attempt(), reason));
    }

    /** 해석되지 않은 메시지를 원문 그대로 격리한다. 어느 자리인지는 알 수 없다. */
    public void isolateRaw(String messageId, Map<String, String> fields, String reason) {
        deadLetterQueue.isolateRaw(messageId, fields, reason);

        operatorNotifier.notifyFailure(
                "예매 메시지 해석 실패 — DLQ 격리",
                """
                messageId : %s
                fields    : %s
                reason    : %s

                내용을 읽을 수 없어 어느 자리인지 특정하지 못했습니다. 원문을 직접
                확인해야 합니다."""
                        .formatted(messageId, fields, reason));
    }
}
