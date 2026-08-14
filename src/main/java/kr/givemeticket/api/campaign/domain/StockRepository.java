package kr.givemeticket.api.campaign.domain;

public interface StockRepository {

    void initialize(Long campaignId, int totalStock);

    StockDecreaseResult decrease(Long campaignId);

    /**
     * 신청 실패·취소로 자리를 되돌린다. {@code upperBound}를 넘지 못하게 막아서,
     * 복원이 중복 호출돼도 재고가 정원보다 커지지 않는다.
     */
    void restore(Long campaignId, int upperBound);

    /**
     * 관리자의 정원 증원.
     */
    void increaseBy(Long campaignId, int delta);

    /**
     * 초기화된 적 없거나 삭제된 캠페인이면 null.
     */
    Long getRemaining(Long campaignId);

    void remove(Long campaignId);
}
