package kr.givemeticket.api.apply.ui.dto.response;

import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;

public record CancelApplicationResponse(
        Long id,
        Long campaignId,
        ApplicationStatus status
) {

    public static CancelApplicationResponse from(ApplicationResponse response) {
        return new CancelApplicationResponse(
                response.id(),
                response.campaignId(),
                response.status()
        );
    }
}
