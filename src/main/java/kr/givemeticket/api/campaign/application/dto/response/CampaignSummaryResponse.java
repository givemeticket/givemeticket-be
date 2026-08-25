package kr.givemeticket.api.campaign.application.dto.response;

import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.domain.Campaign;

/**
 * @param remainingStock      조회 시점의 잔여 재고. 삭제됐거나 재고를 읽지 못하면 null
 * @param myApplicationStatus scope=participated 일 때만 채워진다
 */
public record CampaignSummaryResponse(
        CampaignResponse campaign,
        CampaignOwnerInfo owner,
        Long remainingStock,
        ApplicationStatus myApplicationStatus
) {

    public static CampaignSummaryResponse of(
            Campaign campaign,
            CampaignOwnerInfo owner,
            Long remainingStock,
            ApplicationStatus myApplicationStatus
    ) {
        return new CampaignSummaryResponse(
                CampaignResponse.of(campaign), owner, remainingStock, myApplicationStatus);
    }
}
