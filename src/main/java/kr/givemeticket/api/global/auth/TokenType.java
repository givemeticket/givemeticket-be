package kr.givemeticket.api.global.auth;

public enum TokenType {

    /**
     * 소셜 인증은 끝났지만 아직 가입하지 않은 사용자에게 주는 임시 토큰. 회원가입에만 쓸 수 있다.
     * subject 는 제공자 회원번호다.
     */
    PROVIDER,

    /**
     * 가입이 끝난 사용자의 API 접근 토큰. subject 는 우리 서비스의 userId 다.
     */
    ACCESS
}
