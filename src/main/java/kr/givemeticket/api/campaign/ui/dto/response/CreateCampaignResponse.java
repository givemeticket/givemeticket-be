package kr.givemeticket.api.campaign.ui.dto.response;

import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;

public record CreateCampaignResponse(Long id) {

    public static CreateCampaignResponse from(CampaignResponse response) {
        return new CreateCampaignResponse(response.id());
    }
}
