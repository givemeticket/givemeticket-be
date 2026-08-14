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
     * 잔여 재고와 매진 여부는 {@code GET /campaigns/{campaignId}/stock} 으로 분리했다.
     *
     * @param openAt              UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
     * @param eventAt             위와 같다
     * @param myApplicationStatus scope=participated 일 때만 채워진다
     */
    public record CampaignItem(
            Long id,
            Long ownerId,
            String shortCode,
            String title,
            int totalStock,
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
            return new CampaignItem(
                    campaign.id(),
                    campaign.ownerId(),
                    campaign.shortCode(),
                    campaign.title(),
                    campaign.totalStock(),
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
