package kr.givemeticket.payment;

public record PaymentChargeResponse(
        String status,
        String transactionId
) {

    public static PaymentChargeResponse approved(String transactionId) {
        return new PaymentChargeResponse(StoredPayment.APPROVED, transactionId);
    }

    public static PaymentChargeResponse declined() {
        return new PaymentChargeResponse(StoredPayment.DECLINED, null);
    }

    public static PaymentChargeResponse from(StoredPayment stored) {
        return new PaymentChargeResponse(stored.status(), stored.transactionId());
    }
}
