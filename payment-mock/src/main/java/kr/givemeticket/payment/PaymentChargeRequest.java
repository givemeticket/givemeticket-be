package kr.givemeticket.payment;

public record PaymentChargeRequest(
        String paymentKey,
        Long applicationId,
        Long userId
) {
}
