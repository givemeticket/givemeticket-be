package kr.givemeticket.api.apply.application.dto.response;

import kr.givemeticket.api.payment.domain.RefundStatus;

public record CancelResponse(
        ApplicationResponse application,
        RefundStatus refundStatus
) {
}
