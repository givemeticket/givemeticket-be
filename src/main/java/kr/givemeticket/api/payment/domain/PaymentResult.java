package kr.givemeticket.api.payment.domain;

public record PaymentResult(
        boolean approved,
        String transactionId
) {

    public static PaymentResult approved(String transactionId) {
        return new PaymentResult(true, transactionId);
    }

    public static PaymentResult declined() {
        return new PaymentResult(false, null);
    }
}
