package kr.givemeticket.api.apply.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.global.time.Utc;

/**
 * @param expiresAt UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
 * @param createdAt 위와 같다
 */
public record GetApplicationResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        FailureReason failureReason,
        String transactionId,
        Instant expiresAt,
        Instant createdAt
) {

    public static GetApplicationResponse from(ApplicationResponse response) {
        return new GetApplicationResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                response.failureReason(),
                response.transactionId(),
                Utc.toInstant(response.expiresAt()),
                Utc.toInstant(response.createdAt())
        );
    }
}
