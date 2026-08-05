package kr.givemeticket.api.campaign.domain;

public record StockDecreaseResult(Status status, long remaining) {

    public enum Status {
        SUCCESS,
        SOLD_OUT,
        NOT_INITIALIZED
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

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
