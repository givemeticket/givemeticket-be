package kr.givemeticket.api.apply.domain;

/**
 * 주최자가 신청 하나를 취소했다. 취소 UPDATE 와 같은 트랜잭션에서 발행된다.
 */
public record ApplicationCancelledByOwnerEvent(Long applicationId, Long campaignId, Long userId) {
}
