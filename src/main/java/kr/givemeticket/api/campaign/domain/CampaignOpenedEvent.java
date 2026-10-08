package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;

/**
 * 행사의 신청이 열렸다. 오픈 스케줄러의 트랜잭션 안에서 발행된다.
 *
 * <p>오픈 시각을 미뤘다가 다시 열리면 한 번 더 발행된다. 받는 쪽은 openAt 으로 둘을 구분한다.
 */
public record CampaignOpenedEvent(Long campaignId, String title, String shortCode, LocalDateTime openAt) {
}
