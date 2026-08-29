package kr.givemeticket.api.apply.ui.dto.response;

import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

/**
 * 주최자가 신청자를 내보낸 결과. 본인 취소 응답과 달리 누구의 신청이었는지(userId)와
 * 사유가 함께 내려간다 — 목록에서 어느 줄을 지울지 프론트가 알아야 한다.
 */
public record CancelApplicantResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        FailureReason failureReason
) {

    public static CancelApplicantResponse from(ApplicationResponse response) {
        return new CancelApplicantResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                response.failureReason());
    }
}
