package kr.givemeticket.api.campaign.domain;

import java.util.List;

/**
 * 신청자에게 알릴 항목이 바뀌었다. 수정 트랜잭션 안에서 발행되므로, 받는 쪽이
 * 같은 트랜잭션에 기록을 남기면 "수정은 됐는데 알림은 없다"가 생기지 않는다.
 *
 * @param title 바뀐 뒤의 제목
 */
public record CampaignChangedEvent(
        Long campaignId,
        String title,
        String shortCode,
        List<CampaignChange> changes
) {
}
