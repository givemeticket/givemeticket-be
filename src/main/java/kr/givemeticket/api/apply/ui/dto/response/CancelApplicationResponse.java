package kr.givemeticket.api.apply.ui.dto.response;

import kr.givemeticket.api.apply.application.dto.response.CancelResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.payment.domain.RefundStatus;

/**
 * @param refundStatus 결제가 없던 신청은 NOT_REQUIRED. 환불 요청이 실패하면 PENDING_RETRY이며,
 *                     그 경우에도 신청 취소와 재고 반납은 이미 끝난 상태다
 */
public record CancelApplicationResponse(
        Long id,
        Long campaignId,
        ApplicationStatus status,
        RefundStatus refundStatus
) {

    public static CancelApplicationResponse from(CancelResponse response) {
        return new CancelApplicationResponse(
                response.application().id(),
                response.application().campaignId(),
                response.application().status(),
                response.refundStatus()
        );
    }
}
