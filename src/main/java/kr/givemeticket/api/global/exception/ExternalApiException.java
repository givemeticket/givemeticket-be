package kr.givemeticket.api.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 결제 게이트웨이, 소셜 로그인 제공자처럼 우리가 통제할 수 없는 외부 시스템 호출이 실패했을 때.
 * 서버 결함과 구분해 EXTERNAL_ERROR 로 집계하려고 별도 계층으로 둔다.
 */
public class ExternalApiException extends BusinessException {

    protected ExternalApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
