package kr.givemeticket.api.apply.ui.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

public record GetApplicationResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        FailureReason failureReason,
        String transactionId,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {

    public static GetApplicationResponse from(ApplicationResponse response) {
        return new GetApplicationResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                response.failureReason(),
                response.transactionId(),
                response.expiresAt(),
                response.createdAt()
        );
    }
}
