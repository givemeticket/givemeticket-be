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
    USER_WITHDRAWN
}
