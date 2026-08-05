package kr.givemeticket.api.payment.infrastructure.dto;

public record PaymentChargeResponse(
        String status,
        String transactionId
) {

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(status);
    }
}
