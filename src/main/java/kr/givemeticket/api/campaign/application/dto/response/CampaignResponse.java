package kr.givemeticket.api.campaign.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
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
        boolean requiresPayment,
        CampaignStatus status,
        CampaignDetailInfo detail
) {

    public static CampaignResponse of(Campaign campaign) {
        return of(CampaignSnapshot.from(campaign));
    }

    public static CampaignResponse of(CampaignSnapshot campaign) {
        return new CampaignResponse(
                campaign.id(),
                campaign.ownerId(),
                campaign.shortCode(),
                campaign.title(),
                campaign.type(),
                campaign.totalStock(),
                campaign.openAt(),
                campaign.requiresPayment(),
                campaign.status(),
                CampaignDetailInfo.from(campaign.detail())
        );
    }
}
