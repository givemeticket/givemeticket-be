package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailInfo;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSummaryResponse;
import kr.givemeticket.api.campaign.domain.CampaignStatus;

public record GetCampaignsResponse(List<CampaignItem> campaigns) {

    /**
     * 목록 카드에 필요한 것만 편다. 본문(content)은 길어서 상세 조회에서만 내려간다.
     *
     * @param myApplicationStatus scope=participated 일 때만 채워진다
     */
    public record CampaignItem(
            Long id,
            String shortCode,
            String title,
            int totalStock,
            long remainingStock,
            LocalDateTime openAt,
            boolean requiresPayment,
            CampaignStatus status,
            boolean soldOut,
            LocalDateTime eventAt,
            String location,
            String imageUrl,
            ApplicationStatus myApplicationStatus
    ) {

        private static CampaignItem from(CampaignSummaryResponse summary) {
            CampaignResponse campaign = summary.campaign();
            CampaignDetailInfo detail = campaign.detail();
            return new CampaignItem(
                    campaign.id(),
                    campaign.shortCode(),
                    campaign.title(),
                    campaign.totalStock(),
                    campaign.remainingStock(),
                    campaign.openAt(),
                    campaign.requiresPayment(),
                    campaign.status(),
                    campaign.soldOut(),
                    (detail == null) ? null : detail.eventAt(),
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
