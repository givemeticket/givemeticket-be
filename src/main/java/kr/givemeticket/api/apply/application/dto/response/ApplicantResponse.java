package kr.givemeticket.api.apply.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.user.application.dto.response.UserResponse;

/**
 * 주최자가 보는 신청자 한 명. 신청 정보와 신청자 정보를 함께 담는다 —
 * 목록을 그리려고 userId 마다 사용자 조회를 다시 부르게 하지 않기 위해서다.
 *
 * @param nickname        신청자를 찾지 못하면 null. 이름이 없다고 목록이 비면 안 된다
 * @param profileImageUrl 소셜 프로필 이미지. 동의 항목이 꺼져 있었으면 null
 * @param appliedAt       자리를 잡은 시각. 목록의 정렬 기준이자 선착순 순서다
 */
public record ApplicantResponse(
        Long applicationId,
        Long userId,
        String nickname,
        String profileImageUrl,
        ApplicationStatus status,
        LocalDateTime appliedAt
) {

    public static ApplicantResponse of(Application application, UserResponse user) {
        return new ApplicantResponse(
                application.getId(),
                application.getUserId(),
                (user == null) ? null : user.nickname(),
                (user == null) ? null : user.profileImageUrl(),
                application.getStatus(),
                application.appliedAt());
    }
}
