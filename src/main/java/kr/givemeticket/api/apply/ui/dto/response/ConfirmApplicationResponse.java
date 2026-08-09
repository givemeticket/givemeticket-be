package kr.givemeticket.api.apply.ui.dto.response;

import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

public record ConfirmApplicationResponse(
        Long id,
        Long campaignId,
        ApplicationStatus status,
        FailureReason failureReason,
        String transactionId
) {

    public static ConfirmApplicationResponse from(ApplicationResponse response) {
        return new ConfirmApplicationResponse(
                response.id(),
                response.campaignId(),
                response.status(),
                response.failureReason(),
                response.transactionId()
        );
    }
}
