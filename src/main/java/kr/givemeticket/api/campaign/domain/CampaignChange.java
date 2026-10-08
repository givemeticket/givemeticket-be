package kr.givemeticket.api.campaign.domain;

/**
 * 신청자에게 알려야 하는 변경 한 건. 값은 화면에 그대로 옮길 수 있게 문자열로 들고 있는다.
 *
 * @param before 비어 있었으면 null
 * @param after  지웠으면 null
 */
public record CampaignChange(Field field, String before, String after) {

    /**
     * 신청자가 이미 자리를 잡은 뒤에도 의미가 있는 항목만 둔다. 오픈 시각·정원은 신청자와 무관하고,
     * 본문·포스터·참가비는 고칠 때마다 알림이 가면 소음이다.
     */
    public enum Field {
        TITLE,
        EVENT_AT,
        EVENT_END_AT,
        LOCATION,
        ADDRESS
    }
}
