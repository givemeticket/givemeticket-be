package kr.givemeticket.api.campaign.ui.dto.response;

import kr.givemeticket.api.campaign.application.dto.response.CampaignStockResponse;

/**
 * @param soldOut 잔여 재고 0. 저장된 상태가 아니라 조회 시점의 파생값이다
 */
public record GetCampaignStockResponse(
        Long campaignId,
        long remainingStock,
        boolean soldOut
) {

    public static GetCampaignStockResponse from(CampaignStockResponse response) {
        return new GetCampaignStockResponse(
                response.campaignId(),
                response.remainingStock(),
                response.soldOut()
        );
    }
}
