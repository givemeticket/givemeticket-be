package kr.givemeticket.api.user.domain;

import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserException extends BusinessException {

    private UserException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    /**
     * 제공자 토큰은 멀쩡한데 가입 기록이 없다. /code 가 401을 준 계정으로 로그인을 시도한 경우다.
     */
    public static UserException notFound() {
        return new UserException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                "가입되지 않은 계정입니다. 회원가입을 먼저 진행해 주세요.");
    }

}
