package kr.givemeticket.api.notification.domain;

import java.util.List;

/**
 * 알림이 만들어진 시점의 내용. 행사를 조인해서 문구를 만들지 않는다 — 그러면 나중에 행사가 또
 * 바뀌었을 때 지난 알림의 문구까지 바뀌고, 삭제된 행사는 제목조차 못 그린다.
 *
 * <p>시각도 문자열(UTC, Z)이다. JSON 컬럼에 들어가는 값이라 직렬화 설정에 따라 모양이
 * 달라지지 않게 한다.
 *
 * <p>필드를 더해도 옛 행은 그대로 읽힌다. 없던 필드는 null 로 채워진다.
 *
 * @param ownerNickname 알림이 만들어질 때의 주최자 닉네임. 이 필드가 생기기 전 알림이나
 *                      주최자를 찾을 수 없으면 null
 * @param changes       CAMPAIGN_CHANGED 에서만 채워진다. 그 밖에는 빈 목록
 * @param eventAt       EVENT_REMINDER 에서만 채워진다
 * @param openAt        WISHED_CAMPAIGN_OPENING_SOON 에서만 채워진다
 * @param closedAt      CAMPAIGN_CLOSED 에서만 채워진다
 */
public record NotificationPayload(
        String campaignTitle,
        String shortCode,
        String ownerNickname,
        List<Change> changes,
        String eventAt,
        String openAt,
        String closedAt
) {

    /**
     * @param field  TITLE / OPEN_AT / EVENT_AT / EVENT_END_AT / LOCATION / ADDRESS
     * @param before 비어 있었으면 null
     * @param after  지웠으면 null
     */
    public record Change(String field, String before, String after) {
    }

    public static NotificationPayload of(String campaignTitle, String shortCode, String ownerNickname) {
        return new NotificationPayload(
                campaignTitle, shortCode, ownerNickname, List.of(), null, null, null);
    }

    public NotificationPayload withChanges(List<Change> changes) {
        return new NotificationPayload(
                campaignTitle, shortCode, ownerNickname, List.copyOf(changes), eventAt, openAt, closedAt);
    }

    public NotificationPayload withEventAt(String eventAt) {
        return new NotificationPayload(
                campaignTitle, shortCode, ownerNickname, changes, eventAt, openAt, closedAt);
    }

    public NotificationPayload withOpenAt(String openAt) {
        return new NotificationPayload(
                campaignTitle, shortCode, ownerNickname, changes, eventAt, openAt, closedAt);
    }

    public NotificationPayload withClosedAt(String closedAt) {
        return new NotificationPayload(
                campaignTitle, shortCode, ownerNickname, changes, eventAt, openAt, closedAt);
    }
}
