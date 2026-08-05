package kr.givemeticket.api.payment.domain;

public interface PaymentClient {

    PaymentResult charge(Long applicationId, Long userId);
}
