package kr.givemeticket.api.campaign.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.domain.Campaign;

/**
 * @param remainingStock      조회 시점의 잔여 재고. 삭제됐거나 재고를 읽지 못하면 null
 * @param myApplicationStatus scope=participated 일 때만 채워진다
 * @param myAppliedAt         내가 신청한 시각. 목록의 정렬 기준이며 위와 같이 participated 전용이다
 * @param wishCount           이 행사를 찜한 사람 수
 * @param wished              내가 찜했는지. 로그인하지 않고 조회하면 null
 */
public record CampaignSummaryResponse(
        CampaignResponse campaign,
        CampaignOwnerInfo owner,
        Long remainingStock,
        ApplicationStatus myApplicationStatus,
        LocalDateTime myAppliedAt,
        long wishCount,
        Boolean wished
) {

    /**
     * @param mine 내 신청. 내가 만든 행사 목록처럼 신청과 무관한 조회에서는 null
     */
    public static CampaignSummaryResponse of(
            Campaign campaign,
            CampaignOwnerInfo owner,
            Long remainingStock,
            Application mine,
            long wishCount,
            Boolean wished
    ) {
        return new CampaignSummaryResponse(
                CampaignResponse.of(campaign),
                owner,
                remainingStock,
                (mine == null) ? null : mine.getStatus(),
                (mine == null) ? null : mine.appliedAt(),
                wishCount,
                wished);
    }
}
