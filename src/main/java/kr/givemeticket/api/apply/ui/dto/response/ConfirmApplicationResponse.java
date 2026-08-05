package kr.givemeticket.api.apply.ui.dto.response;

import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;

public record ConfirmApplicationResponse(
        Long id,
        ApplicationStatus status,
        String transactionId
) {

    public static ConfirmApplicationResponse from(ApplicationResponse response) {
        return new ConfirmApplicationResponse(
                response.id(),
                response.status(),
                response.transactionId()
        );
    }
}
