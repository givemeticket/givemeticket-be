package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.Instant;
import java.util.List;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailInfo;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSummaryResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.global.time.Utc;

public record GetCampaignsResponse(List<CampaignItem> campaigns) {

    /**
     * 목록 카드에 필요한 것만 편다. 본문(content)은 길어서 상세 조회에서만 내려간다.
     * 카드마다 재고를 따로 부르지 않아도 되도록 잔여 재고는 여기 함께 담는다.
     *
     * @param openAt              UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
     * @param eventAt             위와 같다
     * @param remainingStock      조회 시점의 잔여 재고. 삭제된 행사이거나 읽지 못했으면 null
     * @param soldOut             잔여 재고 0. remainingStock 이 null 이면 함께 null
     * @param status              삭제된 행사도 목록에 남으므로 DELETED 로 올 수 있다
     * @param myApplicationStatus scope=participated 일 때만 채워진다
     */
    public record CampaignItem(
            Long id,
            CampaignOwnerResponsePart owner,
            String shortCode,
            String title,
            int totalStock,
            Long remainingStock,
            Boolean soldOut,
            Instant openAt,
            boolean requiresPayment,
            CampaignStatus status,
            Instant eventAt,
            String location,
            String imageUrl,
            ApplicationStatus myApplicationStatus
    ) {

        private static CampaignItem from(CampaignSummaryResponse summary) {
            CampaignResponse campaign = summary.campaign();
            CampaignDetailInfo detail = campaign.detail();
            Long remainingStock = summary.remainingStock();
            return new CampaignItem(
                    campaign.id(),
                    CampaignOwnerResponsePart.from(summary.owner()),
                    campaign.shortCode(),
                    campaign.title(),
                    campaign.totalStock(),
                    remainingStock,
                    (remainingStock == null) ? null : remainingStock <= 0,
                    Utc.toInstant(campaign.openAt()),
                    campaign.requiresPayment(),
                    campaign.status(),
                    (detail == null) ? null : Utc.toInstant(detail.eventAt()),
                    (detail == null) ? null : detail.location(),
                    (detail == null) ? null : detail.imageUrl(),
                    summary.myApplicationStatus()
            );
        }
    }

    public static GetCampaignsResponse from(List<CampaignSummaryResponse> summaries) {
        return new GetCampaignsResponse(summaries.stream().map(CampaignItem::from).toList());
    }
}
