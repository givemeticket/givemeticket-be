package kr.givemeticket.api.campaign.application.dto.response;

import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.domain.Campaign;

/**
 * @param ownerNickname       개설자 닉네임. 목록 카드에 바로 그릴 수 있게 함께 내려간다
 * @param myApplicationStatus scope=participated 일 때만 채워진다
 */
public record CampaignSummaryResponse(
        CampaignResponse campaign,
        String ownerNickname,
        ApplicationStatus myApplicationStatus
) {

    public static CampaignSummaryResponse of(
            Campaign campaign,
            String ownerNickname,
            ApplicationStatus myApplicationStatus
    ) {
        return new CampaignSummaryResponse(
                CampaignResponse.of(campaign), ownerNickname, myApplicationStatus);
    }
}
