package kr.givemeticket.api.apply.domain;

/**
 * 예매 식별자를 발급한다. DB 가 아니라 여기서 나오는 이유는 <b>저장보다 먼저</b>
 * 번호가 정해져야 응답을 즉시 내보낼 수 있기 때문이다.
 *
 * <p>언제: 신청이 좌석을 잡은 뒤, 큐에 넣기 직전.
 */
public interface ApplicationIdIssuer {

    /** 다음 식별자. 발급만 받고 쓰지 않은 번호는 버려지므로 id 는 연속이 아니다. */
    long issue();
}
