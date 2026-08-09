package kr.givemeticket.api.campaign.domain;

/**
 * FULL(매진)은 여기에 없다. 잔여 재고가 0인지로 조회 시점에 파생시킨다.
 * 저장해두면 재고가 움직일 때마다 동기화해야 하고, 그 동기화가 어긋나는 순간
 * "재고는 있는데 매진으로 보이는" 버그가 생긴다.
 */
public enum CampaignStatus {
    SCHEDULED,
    OPEN,
    CLOSED,
    DELETED
}
