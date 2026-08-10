package kr.givemeticket.api.login.domain;

/**
 * 소셜 로그인 제공자에게 인가 코드를 넘겨 제공자 회원번호를 받아온다.
 * 토큰 획득과 검증을 모두 끝낸 결과만 밖으로 내보낸다.
 *
 * <p>회원번호가 문자열인 것은 제공자마다 형식이 다르기 때문이다.
 * 카카오는 숫자지만 네이버는 영숫자 문자열이다.
 */
public interface LoginClient {

    Provider provider();

    String fetchProviderId(AuthCodeCommand command);
}
