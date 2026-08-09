package kr.givemeticket.api.payment.domain;

public enum RefundStatus {

    /** 결제가 없던 신청이라 환불할 것이 없다. */
    NOT_REQUIRED,

    COMPLETED,

    /**
     * 취소는 됐지만 환불 요청이 실패했다. 신청 취소 자체는 되돌리지 않는다 —
     * 사용자 입장의 취소는 이미 끝났고, 환불은 뒤에서 다시 시도하면 되는 문제다.
     */
    PENDING_RETRY
}
