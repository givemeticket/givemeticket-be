package kr.givemeticket.api.campaign.domain;

public record StockDecreaseResult(Status status, long remaining) {

    public enum Status {
        SUCCESS,
        SOLD_OUT,
        NOT_INITIALIZED,

        /** 이미 자리를 갖고 있다. 예전에는 DB 유니크 제약이 잡아 주던 경우다. */
        ALREADY_APPLIED
    }

    public static StockDecreaseResult success(long remaining) {
        return new StockDecreaseResult(Status.SUCCESS, remaining);
    }

    public static StockDecreaseResult soldOut() {
        return new StockDecreaseResult(Status.SOLD_OUT, 0);
    }

    public static StockDecreaseResult notInitialized() {
        return new StockDecreaseResult(Status.NOT_INITIALIZED, 0);
    }

    public static StockDecreaseResult alreadyApplied() {
        return new StockDecreaseResult(Status.ALREADY_APPLIED, 0);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
