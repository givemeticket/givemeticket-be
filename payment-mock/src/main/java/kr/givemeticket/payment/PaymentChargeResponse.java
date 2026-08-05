package kr.givemeticket.payment;

public record PaymentChargeResponse(
        String status,
        String transactionId
) {

    public static PaymentChargeResponse approved(String transactionId) {
        return new PaymentChargeResponse("APPROVED", transactionId);
    }

    public static PaymentChargeResponse declined() {
        return new PaymentChargeResponse("DECLINED", null);
    }
}
