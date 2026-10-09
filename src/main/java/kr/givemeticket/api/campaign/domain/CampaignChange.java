package kr.givemeticket.api.campaign.domain;

/**
 * 신청자에게 알려야 하는 변경 한 건. 값은 화면에 그대로 옮길 수 있게 문자열로 들고 있는다.
 *
 * @param before 비어 있었으면 null
 * @param after  지웠으면 null
 */
public record CampaignChange(Field field, String before, String after) {

    /**
     * 신청자나 찜한 사람이 알아야 하는 항목만 둔다. 오픈 시각은 오픈을 기다리는 찜한 사람에게
     * 필요하다. 정원은 신청과 무관하고, 본문·포스터·참가비는 고칠 때마다 알림이 가면 소음이다.
     *
     * <p>행사 일시·장소·주소는 주최자가 안내 정보(detail)를 적은 행사에서만 바뀔 수 있다.
     */
    public enum Field {
        TITLE,
        OPEN_AT,
        EVENT_AT,
        EVENT_END_AT,
        LOCATION,
        ADDRESS
    }
}
