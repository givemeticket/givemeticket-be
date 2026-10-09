package kr.givemeticket.api.campaign.application.dto.response;

import java.util.List;

/**
 * @param nextCursor 다음 페이지를 부를 때 넘길 값. 더 없으면 null
 * @param totalCount 조건에 맞는 전체 건수. 커서와 상관없이 같은 값이다
 */
public record CampaignSearchResponse(
        List<CampaignSummaryResponse> campaigns,
        String nextCursor,
        long totalCount
) {
}
