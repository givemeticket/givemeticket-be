package kr.givemeticket.api.apply.ui.dto.response;

import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;

/**
 * 신청은 자리를 잡는 즉시 확정된다. 결제 단계가 없으므로 status 는 항상 CONFIRMED 다.
 */
public record ApplyResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status
) {

    public static ApplyResponse from(ApplicationResponse response) {
        return new ApplyResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status()
        );
    }
}
