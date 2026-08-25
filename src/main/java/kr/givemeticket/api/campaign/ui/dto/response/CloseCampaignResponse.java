package kr.givemeticket.api.campaign.ui.dto.response;

import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;

/**
 * @param status 항상 CLOSED다. 프론트가 응답만 보고 카드 상태를 갱신할 수 있게 함께 내려간다
 */
public record CloseCampaignResponse(
        Long id,
        String shortCode,
        CampaignStatus status
) {

    public static CloseCampaignResponse from(CampaignResponse response) {
        return new CloseCampaignResponse(response.id(), response.shortCode(), response.status());
    }
}
