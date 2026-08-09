package kr.givemeticket.api.campaign.domain;

/**
 * 신청 핫패스가 DB를 건드리지 않고도 판단할 수 있어야 하는 값들.
 * 캠페인이 열릴 때 Redis에 올라가고, 정원이 바뀌면 같이 갱신된다.
 */
public record CampaignState(
        boolean requiresPayment,
        int totalStock
) {
}
