package kr.givemeticket.api.login.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl        카카오 인증 서버 주소(kauth). 토큰 발급과 공개키 조회를 같은 호스트에서 한다
 * @param apiBaseUrl     카카오 API 서버 주소(kapi). 연결 끊기처럼 인증이 끝난 뒤의 호출은 호스트가 다르다
 * @param restApiKey     앱 REST API 키. 토큰 요청의 client_id 이자 ID 토큰의 aud 다
 * @param clientSecret   카카오 콘솔에서 Client Secret 을 '사용함' 으로 켠 경우에만 필요하다.
 *                       켜 두고 보내지 않으면 앱 키가 맞아도 invalid_client 로 거절된다
 * @param adminKey       앱 전체를 대리하는 어드민 키. 탈퇴 시 연결 끊기에만 쓴다.
 *                       비워 두면 연결 끊기를 건너뛰므로 운영에서는 반드시 채운다
 * @param issuer         ID 토큰의 iss 로 와야 하는 값
 * @param jwksPath       공개키(JWKS) 경로
 * @param jwksCacheTtl   받아온 공개키를 다시 쓰는 기간
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 타임아웃
 */
@Validated
@ConfigurationProperties(prefix = "external.kakao")
public record KakaoLoginProperties(
        @NotBlank(message = "baseUrl이 누락되었습니다.") String baseUrl,
        @NotBlank(message = "apiBaseUrl이 누락되었습니다.") String apiBaseUrl,
        @NotBlank(message = "restApiKey가 누락되었습니다.") String restApiKey,
        String clientSecret,
        String adminKey,
        @NotBlank(message = "issuer가 누락되었습니다.") String issuer,
        @NotBlank(message = "jwksPath가 누락되었습니다.") String jwksPath,
        @NotNull(message = "jwksCacheTtl이 누락되었습니다.") Duration jwksCacheTtl,
        @NotNull(message = "connectTimeout이 누락되었습니다.") Duration connectTimeout,
        @NotNull(message = "readTimeout이 누락되었습니다.") Duration readTimeout
) {

}
