package kr.givemeticket.api.apply.domain;

/**
 * 사용자가 직접 누르지 않은 취소의 사유. 상태는 둘 다 CANCELLED 라 화면 문구는 이걸로 갈린다.
 */
public enum FailureReason {

    /**
     * 주최자가 행사를 삭제했다. 신청자 잘못이 아니므로 상태는 CANCELLED 로 두고 이유만 남긴다.
     */
    CAMPAIGN_DELETED,

    /**
     * 신청자가 탈퇴했다. 자리는 반납되어 다른 사람이 신청할 수 있다.
     */
    USER_WITHDRAWN,

    /**
     * 주최자가 이 신청 하나를 취소했다. 행사는 그대로 살아 있고 자리만 반납된다.
     *
     * <p>{@link #CAMPAIGN_DELETED} 와 상태는 같지만 화면 문구가 달라야 한다 —
     * 행사가 사라진 게 아니라 나만 빠진 것이다.
     */
    CANCELLED_BY_OWNER
}
