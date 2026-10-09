package kr.givemeticket.api.campaign.domain;

public enum CampaignSort {

    /** 최근에 만든 행사부터. 정렬을 고르지 않았을 때의 기본값이다. */
    LATEST,

    /** 오픈 시각이 이른 것부터. 같으면 먼저 만든 것부터. */
    OPEN_AT_ASC,

    /** 오픈 시각이 늦은 것부터. 같으면 나중에 만든 것부터. */
    OPEN_AT_DESC
}
