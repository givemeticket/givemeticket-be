package kr.givemeticket.api.login.domain;

/**
 * 소셜 로그인 제공자에게 인가 코드를 넘겨 신원과 프로필을 받아온다.
 * 토큰 획득과 검증을 모두 끝낸 결과만 밖으로 내보낸다.
 */
public interface LoginClient {

    Provider provider();

    ProviderPrincipal fetchPrincipal(AuthCodeCommand command);
}
