package kr.givemeticket.api.apply.application;

import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class ApplyApplicationException extends BusinessException {

    private ApplyApplicationException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static ApplyApplicationException applicationNotFound() {
        return new ApplyApplicationException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND",
                "신청 내역을 찾을 수 없습니다.");
    }

    public static ApplyApplicationException forbidden() {
        return new ApplyApplicationException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "본인의 신청 내역만 접근할 수 있습니다.");
    }

    public static ApplyApplicationException alreadyApplied() {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "ALREADY_APPLIED",
                "이미 신청한 캠페인입니다.");
    }

    public static ApplyApplicationException notCancelable(ApplicationStatus status) {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "APPLICATION_NOT_CANCELABLE",
                "확정된 신청만 취소할 수 있습니다. (현재 상태: " + status + ")");
    }
}
