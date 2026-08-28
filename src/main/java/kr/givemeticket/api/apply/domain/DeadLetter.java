package kr.givemeticket.api.apply.domain;

import java.util.Map;

/**
 * DLQ 에 격리된 메시지 한 건.
 *
 * @param id     재인입할 때 쓰는 식별자
 * @param fields 격리된 원문. 해석되지 않은 메시지도 있어 구조를 강제하지 않는다
 */
public record DeadLetter(String id, Map<String, String> fields, String reason) {

    /** 원문에서 필드 하나를 꺼낸다. 없으면 null. */
    public String field(String name) {
        return fields.get(name);
    }
}
