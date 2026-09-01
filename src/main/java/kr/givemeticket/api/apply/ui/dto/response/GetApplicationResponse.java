package kr.givemeticket.api.apply.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.global.time.Utc;

/**
 * @param failureReason 사용자가 직접 누르지 않은 취소의 사유. 본인이 취소했으면 null
 * @param appliedAt     자리를 잡은 시각. UTC 라 Z 가 붙어 내려간다. 취소 후 다시 신청했다면
 *                      다시 신청한 시각이다. 저장이 아직 끝나지 않았으면 null
 */
public record GetApplicationResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        FailureReason failureReason,
        Instant appliedAt
) {

    public static GetApplicationResponse from(ApplicationResponse response) {
        return new GetApplicationResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                response.failureReason(),
                Utc.toInstant(response.appliedAt())
        );
    }
}
