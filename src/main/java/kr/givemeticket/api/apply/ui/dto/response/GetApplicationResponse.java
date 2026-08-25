package kr.givemeticket.api.apply.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.global.time.Utc;

/**
 * @param failureReason 사용자가 직접 누르지 않은 취소의 사유. 본인이 취소했으면 null
 * @param createdAt     UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
 */
public record GetApplicationResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        FailureReason failureReason,
        Instant createdAt
) {

    public static GetApplicationResponse from(ApplicationResponse response) {
        return new GetApplicationResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                response.failureReason(),
                Utc.toInstant(response.createdAt())
        );
    }
}
