package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;

/**
 * 주최자가 행사를 종료했다. 종료 트랜잭션 안에서, 실제로 상태가 바뀐 경우에만 발행된다.
 *
 * @param closedAt 종료한 시각(UTC). 캠페인에 따로 저장하지 않고 알림에만 남는다
 */
public record CampaignClosedEvent(Long campaignId, LocalDateTime closedAt) {
}
