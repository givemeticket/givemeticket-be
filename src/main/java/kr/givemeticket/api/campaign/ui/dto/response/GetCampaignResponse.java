package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.ViewerRole;

/**
 * @param soldOut        잔여 재고 0. 저장된 상태가 아니라 조회 시점의 파생값이다
 * @param viewerRole     화면을 어떤 모습으로 그릴지 결정한다
 * @param myApplication  내 신청 내역. 없으면 null
 * @param confirmedCount 확정 신청 수. OWNER에게만 내려간다
 */
public record GetCampaignResponse(
        Long id,
        String shortCode,
        String title,
        CampaignType type,
        int totalStock,
        long remainingStock,
        LocalDateTime openAt,
        boolean requiresPayment,
        CampaignStatus status,
        boolean soldOut,
        ViewerRole viewerRole,
        MyApplicationResponse myApplication,
        Long confirmedCount
) {

    public static GetCampaignResponse from(CampaignDetailResponse response) {
        CampaignResponse campaign = response.campaign();
        return new GetCampaignResponse(
                campaign.id(),
                campaign.shortCode(),
                campaign.title(),
                campaign.type(),
                campaign.totalStock(),
                campaign.remainingStock(),
                campaign.openAt(),
                campaign.requiresPayment(),
                campaign.status(),
                campaign.soldOut(),
                response.viewerRole(),
                MyApplicationResponse.from(response.myApplication()),
                response.confirmedCount()
        );
    }
}
