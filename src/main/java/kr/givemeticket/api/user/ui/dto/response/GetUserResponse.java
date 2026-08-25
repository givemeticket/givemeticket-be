package kr.givemeticket.api.user.ui.dto.response;

import kr.givemeticket.api.user.application.dto.response.UserResponse;

/**
 * @param profileImageUrl 소셜 프로필 이미지. 동의 항목이 꺼져 있었거나 탈퇴한 계정이면 null
 * @param withdrawn       탈퇴한 계정이면 true. 닉네임은 "탈퇴한 사용자"로 내려간다
 */
public record GetUserResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        boolean withdrawn
) {

    public static GetUserResponse from(UserResponse user) {
        return new GetUserResponse(
                user.id(),
                user.nickname(),
                user.profileImageUrl(),
                user.withdrawn());
    }
}
