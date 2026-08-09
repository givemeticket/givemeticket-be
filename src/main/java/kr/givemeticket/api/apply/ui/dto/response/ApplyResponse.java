package kr.givemeticket.api.apply.ui.dto.response;

import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

public record ApplyResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        FailureReason failureReason,
        String transactionId
) {

    public static ApplyResponse from(ApplicationResponse response) {
        return new ApplyResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                response.failureReason(),
                response.transactionId()
        );
    }
}
