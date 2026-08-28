package kr.givemeticket.api.admin.application;

import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AdminAccessException extends BusinessException {

    private AdminAccessException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    /**
     * 키가 설정되지 않아 API 자체가 닫혀 있다. <b>404</b> 로 답한다 —
     * 401 은 "키만 있으면 된다"고 알려주는 셈이다.
     */
    public static AdminAccessException notEnabled() {
        return new AdminAccessException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "요청한 경로를 찾을 수 없습니다.");
    }

    /** 키가 틀렸다. */
    public static AdminAccessException invalidKey() {
        return new AdminAccessException(HttpStatus.UNAUTHORIZED, "ADMIN_KEY_INVALID",
                "어드민 키가 올바르지 않습니다.");
    }
}
