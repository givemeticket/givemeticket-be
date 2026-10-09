package kr.givemeticket.api.notification.domain;

/**
 * 화면 문구가 갈리는 단위. 같은 "취소"라도 행사가 사라진 것과 나만 빠진 것은 다르게 말해야 한다.
 */
public enum NotificationType {

    /**
     * 신청하거나 찜한 행사의 제목·오픈 시각·행사 일시·장소가 바뀌었다. payload.changes 에 무엇이
     * 바뀌었는지 담긴다.
     */
    CAMPAIGN_CHANGED,

    /** 신청한 행사가 삭제됐다. 신청도 함께 취소됐다. */
    CAMPAIGN_DELETED,

    /** 주최자가 내 신청을 취소했다. 행사는 그대로 있다. */
    APPLICATION_CANCELLED,

    /**
     * 신청한 행사가 곧 시작한다. payload.eventAt 이 시작 일시다. 주최자가 행사 일시(detail.eventAt)를
     * 적은 행사에만 나간다.
     */
    EVENT_REMINDER,

    /** 주최자가 행사를 종료했다. payload.closedAt 이 종료 시각이다. 확정된 신청은 그대로 유효하다. */
    CAMPAIGN_CLOSED,

    /** 찜한 행사의 신청이 곧 열린다. payload.openAt 이 오픈 시각이다. */
    WISHED_CAMPAIGN_OPENING_SOON,

    /** 찜한 행사의 신청이 열렸다. 받는 사람은 찜한 사람이지 신청자가 아니다. */
    WISHED_CAMPAIGN_OPENED
}
