package kr.givemeticket.payment;

public record PaymentChargeRequest(
        Long applicationId,
        Long userId
) {
}
