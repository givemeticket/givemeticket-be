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
        String transactionId,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getUserId(),
                application.getStatus(),
                application.getFailureReason(),
                application.getTransactionId(),
                application.getExpiresAt(),
                application.getCreatedAt()
        );
    }

    /**
     * 아직 결과가 확정되지 않아 클라이언트가 폴링해야 하는 상태인가.
     */
    public boolean isPending() {
        return status == ApplicationStatus.UNKNOWN
                || status == ApplicationStatus.PENDING
                || status == ApplicationStatus.MANUAL_REVIEW;
    }
}
