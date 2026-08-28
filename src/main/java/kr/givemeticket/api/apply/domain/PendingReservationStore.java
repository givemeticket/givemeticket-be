package kr.givemeticket.api.apply.domain;

import java.util.Optional;

/**
 * 큐에 넣었지만 아직 저장되지 않은 예매를 잠시 들고 있는 곳.
 *
 * <p>언제: 신청 시 기록하고, 조회가 DB 에서 행을 못 찾았을 때 읽는다.
 * 조회는 언제나 DB 를 먼저 보므로 낡은 값이 남아도 해가 없다 — 만료에 맡긴다.
 */
public interface PendingReservationStore {

    /** 저장 대기 중인 예매를 기록한다. */
    void put(PendingReservation reservation);

    /** 저장 대기 중인 예매를 찾는다. 없으면 비어 있다. */
    Optional<PendingReservation> find(Long applicationId);
}
