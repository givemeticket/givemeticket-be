package kr.givemeticket.api.user.application.dto.response;

import kr.givemeticket.api.user.domain.User;

/**
 * 다른 사용자에게도 보여줄 수 있는 정보만 담는다. 소셜 회원번호·제공자·가입 시각은 내보내지 않는다.
 *
 * @param withdrawn 탈퇴한 계정. 닉네임은 대체값이고 프로필 이미지는 없다
 */
public record UserResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        boolean withdrawn
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.isWithdrawn());
    }
}
