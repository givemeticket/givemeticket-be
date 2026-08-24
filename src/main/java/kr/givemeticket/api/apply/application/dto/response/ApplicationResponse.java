package kr.givemeticket.api.apply.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

public record ApplicationResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        FailureReason failureReason,
        LocalDateTime createdAt
) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getUserId(),
                application.getStatus(),
                application.getFailureReason(),
                application.getCreatedAt()
        );
    }
}
