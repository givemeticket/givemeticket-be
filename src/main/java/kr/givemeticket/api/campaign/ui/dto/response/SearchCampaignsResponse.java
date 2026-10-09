package kr.givemeticket.api.campaign.ui.dto.response;

import java.util.List;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSearchResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignsResponse.CampaignItem;

/**
 * 검색 결과 한 페이지. 카드 모양은 {@link GetCampaignsResponse} 와 같다.
 *
 * @param nextCursor 다음 페이지를 부를 때 cursor 로 그대로 넘긴다. 마지막 페이지면 null
 * @param totalCount 조건에 맞는 전체 건수. "검색 결과 N건"에 쓴다
 */
public record SearchCampaignsResponse(List<CampaignItem> campaigns, String nextCursor, long totalCount) {

    public static SearchCampaignsResponse from(CampaignSearchResponse response) {
        return new SearchCampaignsResponse(
                response.campaigns().stream().map(CampaignItem::from).toList(),
                response.nextCursor(),
                response.totalCount());
    }
}
