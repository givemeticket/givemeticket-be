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
        long remainingStock,
        LocalDateTime openAt,
        boolean requiresPayment,
        CampaignStatus status,
        boolean soldOut,
        CampaignDetailInfo detail
) {

    public static CampaignResponse of(Campaign campaign, Long remainingStock) {
        return of(CampaignSnapshot.from(campaign), remainingStock);
    }

    /**
     * 캐시에서 꺼낸 값도, DB 에서 읽은 엔티티도 결국 이 한 곳을 지난다.
     * 재고만 항상 밖에서 받는다 — 캐시에 담긴 적이 없는 값이기 때문이다.
     */
    public static CampaignResponse of(CampaignSnapshot campaign, Long remainingStock) {
        long remaining = (remainingStock == null) ? campaign.totalStock() : remainingStock;
        return new CampaignResponse(
                campaign.id(),
                campaign.ownerId(),
                campaign.shortCode(),
                campaign.title(),
                campaign.type(),
                campaign.totalStock(),
                remaining,
                campaign.openAt(),
                campaign.requiresPayment(),
                campaign.status(),
                remaining <= 0,
                CampaignDetailInfo.from(campaign.detail())
        );
    }
}
