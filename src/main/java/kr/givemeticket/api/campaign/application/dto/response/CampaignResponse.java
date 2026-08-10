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
        long remainingStock,
        LocalDateTime openAt,
        boolean requiresPayment,
        CampaignStatus status,
        boolean soldOut,
        CampaignDetailInfo detail
) {

    public static CampaignResponse of(Campaign campaign, Long remainingStock) {
        long remaining = (remainingStock == null) ? campaign.getTotalStock() : remainingStock;
        return new CampaignResponse(
                campaign.getId(),
                campaign.getOwnerId(),
                campaign.getShortCode(),
                campaign.getTitle(),
                campaign.getType(),
                campaign.getTotalStock(),
                remaining,
                campaign.getOpenAt(),
                campaign.isRequiresPayment(),
                campaign.getStatus(),
                remaining <= 0,
                CampaignDetailInfo.from(campaign.getDetail())
        );
    }
}
