package kr.givemeticket.api.payment.infrastructure.dto;

public record PaymentChargeRequest(
        Long applicationId,
        Long userId
) {
}
