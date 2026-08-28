package kr.givemeticket.api.apply.domain;

/**
 * 예매 영속화를 요청 스레드에서 떼어내는 메인 큐. 구현은 Redis Stream 이다.
 *
 * <p>언제: 신청이 좌석을 잡은 직후 호출된다. 소비는 워커 쪽 관심사라 여기 없다.
 */
public interface ReservationQueue {

    /** 예매 생성 이벤트를 큐에 넣고, 큐가 부여한 메시지 id 를 돌려준다. */
    String publish(ReservationEvent event);
}
