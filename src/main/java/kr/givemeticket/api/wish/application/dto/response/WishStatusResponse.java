package kr.givemeticket.api.wish.application.dto.response;

/**
 * @param wished 내가 찜했는지. 로그인하지 않고 조회하면 null
 */
public record WishStatusResponse(Long campaignId, long wishCount, Boolean wished) {
}
