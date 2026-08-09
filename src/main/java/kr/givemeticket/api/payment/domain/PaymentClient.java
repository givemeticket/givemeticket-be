package kr.givemeticket.api.payment.domain;

public interface PaymentClient {

    /**
     * 예외를 던지지 않고 결과를 반환한다. 호출자가 "거절"과 "모름"을 다르게 처리해야 하는데,
     * 예외로 구분하면 그 분기가 catch 블록에 숨는다.
     *
     * @param paymentKey 멱등키. 같은 키로 다시 요청하면 PG는 첫 결과를 그대로 돌려준다
     */
    PaymentResult charge(String paymentKey, Long applicationId, Long userId);
}
