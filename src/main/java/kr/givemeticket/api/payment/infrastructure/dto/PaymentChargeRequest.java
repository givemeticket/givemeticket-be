package kr.givemeticket.api.payment.infrastructure.dto;

public record PaymentChargeRequest(
        String paymentKey,
        Long applicationId,
        Long userId
) {
}
