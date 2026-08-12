package kr.givemeticket.api.login.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 API 서버(kapi)가 실패할 때 주는 본문. 인증 서버(kauth)의 OAuth 오류 형식
 * ({@link KakaoErrorResponse})과 달리 숫자 코드와 메시지로 온다.
 *
 * @param code 카카오 에러 코드. -401 은 앱 키·권한 문제, -101 은 연결되지 않은 사용자다
 * @param msg  카카오가 남긴 설명
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoApiErrorResponse(

        @JsonProperty("code")
        Integer code,

        @JsonProperty("msg")
        String msg
) {

    private static final int NOT_REGISTERED_USER = -101;

    /**
     * 해당 앱에 연결되어 있지 않은 사용자. 연결 끊기 입장에서는 목표 상태가 이미 이뤄진 것이다.
     */
    public boolean isNotLinked() {
        return code != null && code == NOT_REGISTERED_USER;
    }
}
