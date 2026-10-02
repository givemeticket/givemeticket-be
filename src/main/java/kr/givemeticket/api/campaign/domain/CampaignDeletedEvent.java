package kr.givemeticket.api.campaign.domain;

/**
 * 행사가 삭제됐다. 삭제 표시와 같은 트랜잭션에서 발행된다.
 *
 * <p>이 시점에는 신청 취소가 아직 돌기 전이다. 받는 쪽은 신청자를 "지금 확정 상태"로만 고르면
 * 안 되고, 삭제로 취소된 신청까지 함께 봐야 한다.
 */
public record CampaignDeletedEvent(Long campaignId, String title, String shortCode) {
}
