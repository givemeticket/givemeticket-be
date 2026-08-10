package kr.givemeticket.api.global.auth;

import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AuthException extends BusinessException {

    private AuthException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static AuthException missingToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "MISSING_TOKEN",
                "Authorization 헤더가 필요합니다.");
    }

    public static AuthException malformedAuthorizationHeader() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "MALFORMED_AUTHORIZATION_HEADER",
                "Authorization 헤더는 'Bearer {토큰}' 형식이어야 합니다.");
    }

    public static AuthException expiredToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN",
                "토큰이 만료되었습니다. 다시 로그인해 주세요.");
    }

    public static AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "유효하지 않은 토큰입니다.");
    }

    /**
     * 가입 전 임시 토큰으로 일반 API를 부르는 것처럼 용도가 다른 토큰을 쓴 경우.
     * 서명은 우리 것이라 통과하므로 용도를 따로 확인해야 걸러진다.
     */
    public static AuthException tokenTypeMismatch() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "TOKEN_TYPE_MISMATCH",
                "이 API에서 사용할 수 없는 토큰입니다.");
    }
}
