package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;

/**
 * @param shortCode 공유 링크의 마지막 조각. 프론트가 자기 오리진을 붙여 링크를 만든다
 */
public record CreateCampaignResponse(
        Long id,
        String shortCode,
        String title,
        int totalStock,
        LocalDateTime openAt,
        boolean requiresPayment,
        CampaignDetailResponsePart detail
) {

    public static CreateCampaignResponse from(CampaignResponse response) {
        return new CreateCampaignResponse(
                response.id(),
                response.shortCode(),
                response.title(),
                response.totalStock(),
                response.openAt(),
                response.requiresPayment(),
                CampaignDetailResponsePart.from(response.detail())
        );
    }
}
