package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;

/**
 * 직전 페이지의 마지막 행사. 다음 페이지는 정렬 순서에서 이 행사 바로 뒤부터다.
 *
 * <p>오픈 시각 정렬에서는 오픈 시각이 같은 행사가 여럿일 수 있어서 id 를 함께 둔다.
 *
 * @param openAt 오픈 시각 정렬일 때만 쓴다. 최신순이면 null
 */
public record CampaignCursor(LocalDateTime openAt, Long id) {

    public static CampaignCursor ofId(Long id) {
        return new CampaignCursor(null, id);
    }

    public static CampaignCursor after(Campaign campaign, CampaignSort sort) {
        return (sort == CampaignSort.LATEST)
                ? ofId(campaign.getId())
                : new CampaignCursor(campaign.getOpenAt(), campaign.getId());
    }
}
