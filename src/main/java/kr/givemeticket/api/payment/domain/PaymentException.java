package kr.givemeticket.api.payment.domain;

import kr.givemeticket.api.global.exception.ExternalApiException;
import org.springframework.http.HttpStatus;

public class PaymentException extends ExternalApiException {

    private PaymentException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static PaymentException gatewayError() {
        return new PaymentException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                "결제 게이트웨이 호출에 실패했습니다.");
    }
}
