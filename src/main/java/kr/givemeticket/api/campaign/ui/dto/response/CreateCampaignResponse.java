package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.global.time.Utc;

/**
 * @param shortCode 공유 링크의 마지막 조각. 프론트가 자기 오리진을 붙여 링크를 만든다
 * @param openAt    UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
 */
public record CreateCampaignResponse(
        Long id,
        String shortCode,
        String title,
        int totalStock,
        Instant openAt,
        CampaignDetailResponsePart detail
) {

    public static CreateCampaignResponse from(CampaignResponse response) {
        return new CreateCampaignResponse(
                response.id(),
                response.shortCode(),
                response.title(),
                response.totalStock(),
                Utc.toInstant(response.openAt()),
                CampaignDetailResponsePart.from(response.detail())
        );
    }
}
