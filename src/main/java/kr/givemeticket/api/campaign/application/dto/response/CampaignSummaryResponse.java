package kr.givemeticket.api.campaign.application.dto.response;

import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.domain.Campaign;

/**
 * @param myApplicationStatus scope=participated 일 때만 채워진다
 */
public record CampaignSummaryResponse(
        CampaignResponse campaign,
        ApplicationStatus myApplicationStatus
) {

    public static CampaignSummaryResponse of(Campaign campaign, ApplicationStatus myApplicationStatus) {
        return new CampaignSummaryResponse(CampaignResponse.of(campaign), myApplicationStatus);
    }
}
