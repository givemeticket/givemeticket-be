package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.global.time.Utc;

/**
 * @param openAt UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
 */
public record PatchCampaignResponse(
        Long id,
        String shortCode,
        String title,
        int totalStock,
        Instant openAt,
        CampaignStatus status,
        CampaignDetailResponsePart detail
) {

    public static PatchCampaignResponse from(CampaignResponse response) {
        return new PatchCampaignResponse(
                response.id(),
                response.shortCode(),
                response.title(),
                response.totalStock(),
                Utc.toInstant(response.openAt()),
                response.status(),
                CampaignDetailResponsePart.from(response.detail())
        );
    }
}
