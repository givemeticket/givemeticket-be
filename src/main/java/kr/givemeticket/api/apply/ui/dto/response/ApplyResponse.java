package kr.givemeticket.api.apply.ui.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;

/**
 * @param expiresAt PENDING일 때만 채워진다. 이 시각까지 confirm하지 않으면 자리가 회수된다
 */
public record ApplyResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        LocalDateTime expiresAt
) {

    public static ApplyResponse from(ApplicationResponse response) {
        return new ApplyResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                response.expiresAt()
        );
    }
}
