package kr.givemeticket.api.apply.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;

public record ApplicationResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        String transactionId,
        LocalDateTime createdAt
) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getUserId(),
                application.getStatus(),
                application.getTransactionId(),
                application.getCreatedAt()
        );
    }
}
