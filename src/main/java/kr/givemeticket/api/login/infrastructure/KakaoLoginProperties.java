package kr.givemeticket.api.login.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl        카카오 인증 서버 주소. 토큰 발급과 공개키 조회를 같은 호스트에서 한다
 * @param restApiKey     앱 REST API 키. 토큰 요청의 client_id 이자 ID 토큰의 aud 다
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
        @NotBlank(message = "restApiKey가 누락되었습니다.") String restApiKey,
        @NotBlank(message = "issuer가 누락되었습니다.") String issuer,
        @NotBlank(message = "jwksPath가 누락되었습니다.") String jwksPath,
        @NotNull(message = "jwksCacheTtl이 누락되었습니다.") Duration jwksCacheTtl,
        @NotNull(message = "connectTimeout이 누락되었습니다.") Duration connectTimeout,
        @NotNull(message = "readTimeout이 누락되었습니다.") Duration readTimeout
) {

}
