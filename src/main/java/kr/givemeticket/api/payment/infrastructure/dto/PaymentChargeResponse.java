package kr.givemeticket.api.payment.infrastructure.dto;

public record PaymentChargeResponse(
        String status,
        String transactionId
) {

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(status);
    }

    /**
     * 같은 멱등키의 앞선 요청이 아직 처리 중이다. 승인도 거절도 아니므로 "모름"으로 다뤄야 한다.
     */
    public boolean isProcessing() {
        return "PROCESSING".equalsIgnoreCase(status);
    }
}
