package kr.givemeticket.api.apply.domain;

import java.util.Set;

public enum ApplicationStatus {

    PENDING,

    CONFIRMED,

    FAILED,

    CANCELLED,

    /**
     * 결제 요청은 나갔는데 응답을 받지 못했다. 실패가 아니라 <b>모름</b>이다.
     * 실패로 단정하고 재고를 돌려주면, PG에서 실제로 승인된 경우 돈은 빠져나갔는데
     * 자리는 남에게 팔린다. 그래서 이 상태에서는 재고를 잡아둔 채 PG에 다시 물어본다.
     */
    UNKNOWN,

    /** 정산 재시도 한도를 넘겨 사람이 판단해야 하는 건. 재고는 계속 잡아둔다. */
    MANUAL_REVIEW;

    /**
     * 자리를 차지하고 있는 상태들. 재고 계산과 중복 신청 판정의 기준이다.
     */
    private static final Set<ApplicationStatus> ACTIVE =
            Set.of(PENDING, CONFIRMED, UNKNOWN, MANUAL_REVIEW);

    public static Set<ApplicationStatus> active() {
        return ACTIVE;
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
