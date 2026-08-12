package kr.givemeticket.api.login.domain;

/**
 * 탈퇴할 때 소셜 제공자 쪽 연결까지 끊는다. 우리 DB 에서 지우는 것만으로는
 * 제공자의 '연결된 서비스' 목록에 우리 앱이 그대로 남는다.
 *
 * <p>로그인({@link LoginClient})과 나눠 둔 이유는 필요한 자격 증명이 다르기 때문이다.
 * 로그인은 사용자가 방금 준 인가 코드로 하지만, 연결 끊기는 사용자가 자리에 없는 시점에
 * 우리 서비스 자격으로 해야 한다. 제공자별로 그 수단이 있는지가 갈려서
 * 모든 제공자가 이 인터페이스를 구현하지는 않는다.
 *
 * <p>구현체는 멱등해야 한다. 이미 끊긴 계정에 다시 요청해도 성공으로 끝내야
 * 탈퇴 중간에 실패한 요청을 그대로 재시도할 수 있다.
 */
public interface ProviderUnlinkClient {

    Provider provider();

    /**
     * @param providerId 제공자가 부여한 회원 식별자. 우리 유저 행에 저장해 둔 값이다
     */
    void unlink(String providerId);
}
