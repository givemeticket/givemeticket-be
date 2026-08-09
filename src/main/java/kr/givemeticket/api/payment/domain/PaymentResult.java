package kr.givemeticket.api.payment.domain;

public record PaymentResult(
        PaymentOutcome outcome,
        String transactionId
) {

    public static PaymentResult approved(String transactionId) {
        return new PaymentResult(PaymentOutcome.APPROVED, transactionId);
    }

    public static PaymentResult declined() {
        return new PaymentResult(PaymentOutcome.DECLINED, null);
    }

    public static PaymentResult error() {
        return new PaymentResult(PaymentOutcome.ERROR, null);
    }

    public static PaymentResult unknown() {
        return new PaymentResult(PaymentOutcome.UNKNOWN, null);
    }
}
