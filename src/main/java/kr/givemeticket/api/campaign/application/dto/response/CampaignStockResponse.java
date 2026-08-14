package kr.givemeticket.api.campaign.application.dto.response;

/**
 * 재고만 따로 내려주는 응답. 상세·목록 조회보다 훨씬 자주 폴링되므로 캠페인 정보와 분리했다.
 *
 * @param soldOut 잔여 재고 0. 저장된 상태가 아니라 조회 시점의 파생값이다
 */
public record CampaignStockResponse(
        Long campaignId,
        long remainingStock,
        boolean soldOut
) {

    public static CampaignStockResponse of(Long campaignId, long remainingStock) {
        return new CampaignStockResponse(campaignId, remainingStock, remainingStock <= 0);
    }
}
