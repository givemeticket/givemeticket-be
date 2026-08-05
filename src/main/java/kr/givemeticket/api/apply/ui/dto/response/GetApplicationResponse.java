package kr.givemeticket.api.apply.ui.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;

public record GetApplicationResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        String transactionId,
        LocalDateTime createdAt
) {

    public static GetApplicationResponse from(ApplicationResponse response) {
        return new GetApplicationResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                response.transactionId(),
                response.createdAt()
        );
    }
}
