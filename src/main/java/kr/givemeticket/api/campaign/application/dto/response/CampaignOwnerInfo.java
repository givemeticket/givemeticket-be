package kr.givemeticket.api.campaign.application.dto.response;

import kr.givemeticket.api.user.application.dto.response.UserResponse;

/**
 * 행사 화면에 그릴 개설자 정보. 캠페인 정보와 수명이 같아 조회 응답에 함께 실어 보낸다.
 * userId 만 들고 있는 화면은 {@code GET /users/{userId}} 를 쓰면 된다.
 *
 * @param profileImageUrl 소셜 프로필 이미지. 동의하지 않았으면 null
 */
public record CampaignOwnerInfo(
        Long id,
        String nickname,
        String profileImageUrl
) {

    /**
     * 개설자를 찾지 못하면 id 만 채운다. 이름이 없다고 행사가 안 보이면 안 된다.
     */
    public static CampaignOwnerInfo of(Long ownerId, UserResponse user) {
        if (user == null) {
            return new CampaignOwnerInfo(ownerId, null, null);
        }
        return new CampaignOwnerInfo(user.id(), user.nickname(), user.profileImageUrl());
    }
}
