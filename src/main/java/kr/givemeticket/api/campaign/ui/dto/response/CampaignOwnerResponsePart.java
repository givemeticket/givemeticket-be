package kr.givemeticket.api.campaign.ui.dto.response;

import kr.givemeticket.api.campaign.application.dto.response.CampaignOwnerInfo;

/**
 * @param nickname        개설자를 찾을 수 없으면 null
 * @param profileImageUrl 소셜 프로필 이미지. 동의 항목이 꺼져 있었으면 null
 */
public record CampaignOwnerResponsePart(
        Long id,
        String nickname,
        String profileImageUrl
) {

    public static CampaignOwnerResponsePart from(CampaignOwnerInfo owner) {
        return new CampaignOwnerResponsePart(
                owner.id(), owner.nickname(), owner.profileImageUrl());
    }
}
