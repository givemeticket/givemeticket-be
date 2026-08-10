package kr.givemeticket.api.login.domain;

/**
 * 인가 코드를 토큰으로 바꾸는 데 필요한 값.
 * 제공자마다 쓰는 필드가 다르다 — 카카오는 redirectUrl 을, 네이버는 state 를 요구한다.
 *
 * @param state 제공자에 따라 없을 수 있다. 필요 여부는 각 LoginClient 가 판단한다
 */
public record AuthCodeCommand(Provider provider, String code, String redirectUrl, String state) {

}
