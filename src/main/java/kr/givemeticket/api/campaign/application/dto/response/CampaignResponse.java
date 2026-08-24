package kr.givemeticket.api.campaign.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;

public record CampaignResponse(
        Long id,
        Long ownerId,
        String shortCode,
        String title,
        CampaignType type,
        int totalStock,
        LocalDateTime openAt,
        CampaignStatus status,
        CampaignDetailInfo detail
) {

    public static CampaignResponse of(Campaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getOwnerId(),
                campaign.getShortCode(),
                campaign.getTitle(),
                campaign.getType(),
                campaign.getTotalStock(),
                campaign.getOpenAt(),
                campaign.getStatus(),
                CampaignDetailInfo.from(campaign.getDetail())
        );
    }
}
