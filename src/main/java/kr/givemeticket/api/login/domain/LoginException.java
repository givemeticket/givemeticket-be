package kr.givemeticket.api.login.domain;

import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class LoginException extends BusinessException {

    private LoginException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static LoginException unsupportedProvider(String provider) {
        return new LoginException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER",
                "지원하지 않는 로그인 방식입니다. (요청: " + provider + ")");
    }

    /**
     * 인가 코드는 1회용이라 이미 썼거나 만료된 코드로 다시 오면 제공자가 거부한다.
     */
    public static LoginException invalidAuthorizationCode() {
        return new LoginException(HttpStatus.BAD_REQUEST, "INVALID_AUTHORIZATION_CODE",
                "만료되었거나 이미 사용된 인가 코드입니다. 다시 로그인해 주세요.");
    }

    /**
     * 네이버는 토큰 요청에 state 를 요구한다. 카카오만 쓰던 프론트가 놓치기 쉬워 따로 알려준다.
     */
    public static LoginException stateRequired(String provider) {
        return new LoginException(HttpStatus.BAD_REQUEST, "STATE_REQUIRED",
                provider + " 로그인은 state 값이 필요합니다.");
    }

    /**
     * 서명·발급자·대상·형식 중 무엇이 틀렸는지는 응답에 드러내지 않는다. 위조 시도에 힌트가 된다.
     */
    public static LoginException invalidIdToken() {
        return new LoginException(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN",
                "로그인 정보를 확인할 수 없습니다.");
    }

    public static LoginException expiredIdToken() {
        return new LoginException(HttpStatus.UNAUTHORIZED, "EXPIRED_ID_TOKEN",
                "로그인 정보가 만료되었습니다. 다시 로그인해 주세요.");
    }
}
