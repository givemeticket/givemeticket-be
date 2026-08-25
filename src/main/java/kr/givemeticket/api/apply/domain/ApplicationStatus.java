package kr.givemeticket.api.apply.domain;

import java.util.Set;

public enum ApplicationStatus {

    /** 자리를 잡은 상태. 결제 단계가 없어져 신청은 곧바로 여기로 온다. */
    CONFIRMED,

    CANCELLED;

    /**
     * 자리를 차지하고 있는 상태들. 재고 계산과 중복 신청 판정의 기준이다.
     */
    private static final Set<ApplicationStatus> ACTIVE = Set.of(CONFIRMED);

    public static Set<ApplicationStatus> active() {
        return ACTIVE;
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
