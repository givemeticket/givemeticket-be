package kr.givemeticket.api.login.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 네이버는 토큰 발급 서버와 프로필 API 서버의 호스트가 다르다.
 *
 * @param authBaseUrl    토큰 발급 (nid.naver.com)
 * @param apiBaseUrl     회원 프로필 조회 (openapi.naver.com)
 * @param clientId       애플리케이션 클라이언트 아이디
 * @param clientSecret   애플리케이션 클라이언트 시크릿. 카카오와 달리 필수다
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 타임아웃
 */
@Validated
@ConfigurationProperties(prefix = "external.naver")
public record NaverLoginProperties(
        @NotBlank(message = "authBaseUrl이 누락되었습니다.") String authBaseUrl,
        @NotBlank(message = "apiBaseUrl이 누락되었습니다.") String apiBaseUrl,
        @NotBlank(message = "clientId가 누락되었습니다.") String clientId,
        @NotBlank(message = "clientSecret이 누락되었습니다.") String clientSecret,
        @NotNull(message = "connectTimeout이 누락되었습니다.") Duration connectTimeout,
        @NotNull(message = "readTimeout이 누락되었습니다.") Duration readTimeout
) {

}
