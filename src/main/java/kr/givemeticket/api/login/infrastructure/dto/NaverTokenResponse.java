package kr.givemeticket.api.login.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 네이버는 인가 코드가 잘못돼도 HTTP 200 에 error 필드를 담아 보낸다.
 * 상태 코드만 보고 성공으로 넘기면 안 되므로 error 도 함께 받는다.
 *
 * <p>expiresIn 이 문자열인 것은 오타가 아니라 네이버 응답이 실제로 문자열이기 때문이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverTokenResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        String expiresIn,

        @JsonProperty("error")
        String error,

        @JsonProperty("error_description")
        String errorDescription
) {

    public boolean hasError() {
        return error != null;
    }
}
