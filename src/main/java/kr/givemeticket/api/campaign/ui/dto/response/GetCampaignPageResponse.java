package kr.givemeticket.api.campaign.ui.dto.response;

import java.util.List;
import kr.givemeticket.api.campaign.application.dto.response.CampaignPageResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignsResponse.CampaignItem;

/**
 * 검색처럼 끝없이 길어질 수 있는 목록. 카드 모양은 {@link GetCampaignsResponse} 와 같다.
 *
 * @param nextCursor 다음 페이지를 부를 때 cursor 로 넘긴다. 마지막 페이지면 null
 */
public record GetCampaignPageResponse(List<CampaignItem> campaigns, Long nextCursor) {

    public static GetCampaignPageResponse from(CampaignPageResponse page) {
        return new GetCampaignPageResponse(
                page.campaigns().stream().map(CampaignItem::from).toList(),
                page.nextCursor());
    }
}
