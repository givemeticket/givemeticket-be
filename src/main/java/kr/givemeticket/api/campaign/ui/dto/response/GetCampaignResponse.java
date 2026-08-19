package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.ViewerRole;
import kr.givemeticket.api.global.time.Utc;

/**
 * 첫 화면을 한 번에 그릴 수 있도록 개설자 정보와 잔여 재고까지 함께 담는다.
 * 그 뒤 카운트다운 중 재고 갱신은 {@code GET /campaigns/{campaignId}/stock} 으로 폴링한다.
 *
 * @param openAt         UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
 * @param remainingStock 조회 시점의 잔여 재고. 읽지 못했으면 null 이고, 그때는 재고 표시만 비우면 된다
 * @param soldOut        잔여 재고 0. remainingStock 이 null 이면 함께 null
 * @param viewerRole     화면을 어떤 모습으로 그릴지 결정한다
 * @param myApplication  내 신청 내역. 없으면 null
 * @param confirmedCount 확정 신청 수. OWNER에게만 내려간다
 * @param detail         행사 안내 정보. 등록된 게 없으면 null
 */
public record GetCampaignResponse(
        Long id,
        CampaignOwnerResponsePart owner,
        String shortCode,
        String title,
        CampaignType type,
        int totalStock,
        Long remainingStock,
        Boolean soldOut,
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
        Long remainingStock = response.remainingStock();
        return new GetCampaignResponse(
                campaign.id(),
                CampaignOwnerResponsePart.from(response.owner()),
                campaign.shortCode(),
                campaign.title(),
                campaign.type(),
                campaign.totalStock(),
                remainingStock,
                (remainingStock == null) ? null : remainingStock <= 0,
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
