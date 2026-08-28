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

    /**
     * 큐에 넣기까지만 끝난 예매. 아직 행이 없지만 상태는 {@code CONFIRMED} 다 —
     * 사용자 입장에서 자리는 이미 잡혔다. {@code createdAt} 은 저장 시점에 정해지므로 비운다.
     */
    public static ApplicationResponse accepted(Long id, Long campaignId, Long userId) {
        return new ApplicationResponse(
                id, campaignId, userId, ApplicationStatus.CONFIRMED, null, null);
    }

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
