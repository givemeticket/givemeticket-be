package kr.givemeticket.api.wish.ui.dto.response;

import kr.givemeticket.api.wish.application.dto.response.WishStatusResponse;

/**
 * @param wishCount 이 행사를 찜한 사람 수
 * @param wished    내가 찜했는지. 로그인하지 않고 조회하면 null
 */
public record WishStatusResponsePart(Long campaignId, long wishCount, Boolean wished) {

    public static WishStatusResponsePart from(WishStatusResponse status) {
        return new WishStatusResponsePart(status.campaignId(), status.wishCount(), status.wished());
    }
}
