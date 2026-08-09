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

    /**
     * 결제 결과를 모르는 동안은 취소를 받지 않는다. 환불 대상인지 아닌지가 아직 안 정해졌는데
     * 취소부터 하면 자리는 남에게 넘어가고 돈의 행방만 남는다.
     */
    public static ApplyApplicationException cancelPendingSettlement() {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "APPLICATION_SETTLEMENT_PENDING",
                "결제 결과를 확인하는 중입니다. 잠시 후 다시 시도해 주세요.");
    }

    /**
     * 카드 거절 등 PG가 명시적으로 거부한 경우. 사용자가 다시 시도할 수 있으므로 4xx다.
     */
    public static ApplyApplicationException paymentDeclined() {
        return new ApplyApplicationException(HttpStatus.CONFLICT, "PAYMENT_DECLINED",
                "결제가 거절되었습니다.");
    }
}
