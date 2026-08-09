package kr.givemeticket.payment;

/**
 * 멱등키 하나에 대응하는 결제 상태.
 *
 * <p>실제 PG는 같은 멱등키로 다시 요청하면 결제를 새로 만들지 않고 첫 결과를 그대로 돌려준다.
 * mock이 상태를 갖지 않으면 재시도가 그대로 이중 결제가 되므로 최소한의 저장소를 둔다.
 */
public record StoredPayment(String status, String transactionId) {

    public static final String PROCESSING = "PROCESSING";
    public static final String APPROVED = "APPROVED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";

    public static StoredPayment processing() {
        return new StoredPayment(PROCESSING, null);
    }

    public static StoredPayment approved(String transactionId) {
        return new StoredPayment(APPROVED, transactionId);
    }

    public static StoredPayment declined() {
        return new StoredPayment(DECLINED, null);
    }

    public StoredPayment cancel() {
        return new StoredPayment(CANCELLED, transactionId);
    }

    public boolean isApproved() {
        return APPROVED.equals(status);
    }

    public boolean isCancelled() {
        return CANCELLED.equals(status);
    }
}
