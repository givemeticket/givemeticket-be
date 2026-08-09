package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;

public record PatchCampaignResponse(
        Long id,
        String shortCode,
        int totalStock,
        long remainingStock,
        LocalDateTime openAt,
        CampaignStatus status,
        boolean soldOut
) {

    public static PatchCampaignResponse from(CampaignResponse response) {
        return new PatchCampaignResponse(
                response.id(),
                response.shortCode(),
                response.totalStock(),
                response.remainingStock(),
                response.openAt(),
                response.status(),
                response.soldOut()
        );
    }
}
