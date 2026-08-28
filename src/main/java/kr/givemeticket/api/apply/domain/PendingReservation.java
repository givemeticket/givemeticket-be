package kr.givemeticket.api.apply.domain;

/**
 * 아직 MySQL 에 도달하지 않은 예매. 조회에 답하기 위한 최소 정보만 갖는다.
 *
 * <p>언제: 큐에 넣을 때 만들어지고, 워커가 행을 만들 때까지의 빈틈을 메운다.
 */
public record PendingReservation(Long applicationId, Long campaignId, Long userId) {
}
