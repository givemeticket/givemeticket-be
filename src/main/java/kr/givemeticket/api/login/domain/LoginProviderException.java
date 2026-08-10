package kr.givemeticket.api.login.domain;

import kr.givemeticket.api.global.exception.ExternalApiException;
import org.springframework.http.HttpStatus;

/**
 * 소셜 로그인 제공자 호출 자체가 실패한 경우. 사용자 입력 문제와 구분해 외부 오류로 집계한다.
 */
public class LoginProviderException extends ExternalApiException {

    private LoginProviderException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static LoginProviderException tokenRequestFailed() {
        return new LoginProviderException(HttpStatus.BAD_GATEWAY, "LOGIN_PROVIDER_ERROR",
                "소셜 로그인 제공자 호출에 실패했습니다.");
    }

    /**
     * OIDC 를 지원하지 않는 제공자는 프로필 API 를 한 번 더 호출해야 회원번호를 알 수 있다.
     */
    public static LoginProviderException profileRequestFailed() {
        return new LoginProviderException(HttpStatus.BAD_GATEWAY, "LOGIN_PROVIDER_PROFILE_ERROR",
                "소셜 로그인 회원 정보를 가져오지 못했습니다.");
    }

    public static LoginProviderException publicKeyRequestFailed() {
        return new LoginProviderException(HttpStatus.BAD_GATEWAY, "LOGIN_PROVIDER_KEY_ERROR",
                "소셜 로그인 공개키를 가져오지 못했습니다.");
    }
}
