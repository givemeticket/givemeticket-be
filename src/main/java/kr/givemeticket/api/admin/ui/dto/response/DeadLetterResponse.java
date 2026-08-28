package kr.givemeticket.api.admin.ui.dto.response;

import java.util.Map;
import kr.givemeticket.api.apply.domain.DeadLetter;

/**
 * @param id     재인입할 때 쓰는 식별자
 * @param fields 격리된 원문 그대로. 해석되지 않은 메시지도 있어 구조를 강제하지 않는다
 */
public record DeadLetterResponse(String id, String reason, Map<String, String> fields) {

    public static DeadLetterResponse from(DeadLetter deadLetter) {
        return new DeadLetterResponse(
                deadLetter.id(), deadLetter.reason(), deadLetter.fields());
    }
}
