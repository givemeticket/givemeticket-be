package kr.givemeticket.api.login.domain;

/**
 * 소셜 인증으로 확인된 신원. 아직 우리 서비스의 유저는 아니다.
 */
public record ProviderPrincipal(String providerId, Provider provider) {

}
