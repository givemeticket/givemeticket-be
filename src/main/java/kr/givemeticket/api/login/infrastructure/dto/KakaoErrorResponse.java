package kr.givemeticket.api.login.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오가 토큰 발급을 거절할 때 주는 본문.
 *
 * <p>error 는 OAuth 표준 값이고, errorCode 는 카카오가 붙이는 세부 코드다(KOE320 등).
 * 이걸 남기지 않으면 우리 설정 문제인지 사용자의 인가 코드 문제인지 구분할 수 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoErrorResponse(

        @JsonProperty("error")
        String error,

        @JsonProperty("error_description")
        String errorDescription,

        @JsonProperty("error_code")
        String errorCode
) {

    /**
     * client_id / client_secret 이 틀렸다는 뜻. 사용자가 다시 로그인해도 달라지지 않는다.
     */
    public boolean isClientMisconfigured() {
        return "invalid_client".equals(error);
    }
}
