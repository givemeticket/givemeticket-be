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

    public static ApplyApplicationException notPending(ApplicationStatus status) {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "APPLICATION_NOT_PENDING",
                "결제 대기 중인 신청만 확정할 수 있습니다. (현재 상태: " + status + ")");
    }

    /**
     * 홀드 시간을 넘겼다. 재고는 이미 반납됐으므로 처음부터 다시 신청해야 한다.
     */
    public static ApplyApplicationException expired() {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "APPLICATION_EXPIRED",
                "결제 시간이 만료되었습니다. 다시 신청해 주세요.");
    }

    public static ApplyApplicationException notCancelable(ApplicationStatus status) {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "APPLICATION_NOT_CANCELABLE",
                "확정된 신청만 취소할 수 있습니다. (현재 상태: " + status + ")");
    }

    public static ApplyApplicationException cancelPendingSettlement() {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "APPLICATION_SETTLEMENT_PENDING",
                "결제 결과를 확인하는 중입니다. 잠시 후 다시 시도해 주세요.");
    }

    public static ApplyApplicationException paymentDeclined() {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "PAYMENT_DECLINED",
                "결제가 거절되었습니다.");
    }
}
