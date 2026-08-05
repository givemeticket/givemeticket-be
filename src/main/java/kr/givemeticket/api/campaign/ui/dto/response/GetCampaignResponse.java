package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;

public record GetCampaignResponse(
        Long id,
        String title,
        CampaignType type,
        int totalStock,
        long remainingStock,
        LocalDateTime openAt,
        CampaignStatus status
) {

    public static GetCampaignResponse from(CampaignResponse response) {
        return new GetCampaignResponse(
                response.id(),
                response.title(),
                response.type(),
                response.totalStock(),
                response.remainingStock(),
                response.openAt(),
                response.status()
        );
    }
}
