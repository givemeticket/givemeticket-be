package kr.givemeticket.api.apply.domain;

public enum FailureReason {

    /** 재고 소진. 신청 행 자체가 만들어지지 않으므로 재신청 시에만 기록된다. */
    SOLD_OUT,

    /** 카드 거절 등 PG가 명시적으로 거부. 사용자가 다시 시도할 수 있다. */
    PAYMENT_DECLINED,

    /** PG 5xx·연결 실패. 요청이 처리되지 않은 것이 확실한 경우. */
    PAYMENT_ERROR,

    /** 홀드 시간 안에 결제가 끝나지 않음. */
    EXPIRED
}
