package kr.givemeticket.api.inquiry.application;

import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InquiryApplicationException extends BusinessException {

    private InquiryApplicationException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static InquiryApplicationException notFound() {
        return new InquiryApplicationException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND",
                "문의를 찾을 수 없습니다.");
    }
}
