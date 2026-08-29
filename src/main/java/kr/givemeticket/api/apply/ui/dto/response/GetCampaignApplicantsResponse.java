package kr.givemeticket.api.apply.ui.dto.response;

import java.time.Instant;
import java.util.List;
import kr.givemeticket.api.apply.application.dto.response.ApplicantResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.global.time.Utc;

/**
 * 주최자가 보는 신청자 목록. 신청한 순서대로 담긴다.
 *
 * @param totalCount 목록의 길이. 프론트가 세지 않아도 되도록 함께 내려준다
 */
public record GetCampaignApplicantsResponse(
        Long campaignId,
        int totalCount,
        List<Applicant> applicants
) {

    /**
     * @param nickname  신청자를 찾을 수 없으면 null
     * @param appliedAt UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
     */
    public record Applicant(
            Long applicationId,
            Long userId,
            String nickname,
            String profileImageUrl,
            ApplicationStatus status,
            Instant appliedAt
    ) {

        private static Applicant from(ApplicantResponse applicant) {
            return new Applicant(
                    applicant.applicationId(),
                    applicant.userId(),
                    applicant.nickname(),
                    applicant.profileImageUrl(),
                    applicant.status(),
                    Utc.toInstant(applicant.appliedAt()));
        }
    }

    public static GetCampaignApplicantsResponse of(
            Long campaignId, List<ApplicantResponse> applicants) {
        return new GetCampaignApplicantsResponse(
                campaignId,
                applicants.size(),
                applicants.stream().map(Applicant::from).toList());
    }
}
