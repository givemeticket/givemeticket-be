package kr.givemeticket.api.payment.domain;

public enum PaymentOutcome {

    APPROVED,

    /** PG가 명시적으로 거부. 결제되지 않은 것이 확실하다. */
    DECLINED,

    /** 요청이 PG에 닿지 않았거나 처리되지 않은 것이 확실하다. 재고를 돌려줘도 안전하다. */
    ERROR,

    /**
     * 요청은 나갔는데 응답을 받지 못했다. 승인됐을 수도 있으므로 실패로 단정하면 안 된다.
     * 재고를 잡아둔 채 멱등키로 PG에 다시 물어봐야 한다.
     */
    UNKNOWN
}
