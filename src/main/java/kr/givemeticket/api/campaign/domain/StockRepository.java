package kr.givemeticket.api.campaign.domain;

public interface StockRepository {

    void initialize(Long campaignId, int totalStock);

    StockDecreaseResult decrease(Long campaignId);

    void increase(Long campaignId);

    Long getRemaining(Long campaignId);
}
