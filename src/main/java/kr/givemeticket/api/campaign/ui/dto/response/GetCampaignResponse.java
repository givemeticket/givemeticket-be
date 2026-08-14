package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.ViewerRole;
import kr.givemeticket.api.global.time.Utc;

/**
 * 잔여 재고와 매진 여부는 폴링 주기가 달라 {@code GET /campaigns/{campaignId}/stock} 으로 분리했다.
 *
 * @param openAt         UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
 * @param viewerRole     화면을 어떤 모습으로 그릴지 결정한다
 * @param myApplication  내 신청 내역. 없으면 null
 * @param confirmedCount 확정 신청 수. OWNER에게만 내려간다
 * @param detail         행사 안내 정보. 등록된 게 없으면 null
 */
public record GetCampaignResponse(
        Long id,
        Long ownerId,
        String shortCode,
        String title,
        CampaignType type,
        int totalStock,
        Instant openAt,
        boolean requiresPayment,
        CampaignStatus status,
        ViewerRole viewerRole,
        MyApplicationResponse myApplication,
        Long confirmedCount,
        CampaignDetailResponsePart detail
) {

    public static GetCampaignResponse from(CampaignDetailResponse response) {
        CampaignResponse campaign = response.campaign();
        return new GetCampaignResponse(
                campaign.id(),
                campaign.ownerId(),
                campaign.shortCode(),
                campaign.title(),
                campaign.type(),
                campaign.totalStock(),
                Utc.toInstant(campaign.openAt()),
                campaign.requiresPayment(),
                campaign.status(),
                response.viewerRole(),
                MyApplicationResponse.from(response.myApplication()),
                response.confirmedCount(),
                CampaignDetailResponsePart.from(campaign.detail())
        );
    }
}
