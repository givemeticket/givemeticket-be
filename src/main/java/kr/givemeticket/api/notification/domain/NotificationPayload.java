package kr.givemeticket.api.notification.domain;

import java.util.List;

/**
 * 알림이 만들어진 시점의 내용. 행사를 조인해서 문구를 만들지 않는다 — 그러면 나중에 행사가 또
 * 바뀌었을 때 지난 알림의 문구까지 바뀌고, 삭제된 행사는 제목조차 못 그린다.
 *
 * <p>시각도 문자열(UTC, Z)이다. JSON 컬럼에 들어가는 값이라 직렬화 설정에 따라 모양이
 * 달라지지 않게 한다.
 *
 * @param changes CAMPAIGN_CHANGED 에서만 채워진다. 그 밖에는 빈 목록
 * @param eventAt EVENT_REMINDER 에서만 채워진다
 */
public record NotificationPayload(
        String campaignTitle,
        String shortCode,
        List<Change> changes,
        String eventAt
) {

    /**
     * @param field  TITLE / EVENT_AT / EVENT_END_AT / LOCATION / ADDRESS
     * @param before 비어 있었으면 null
     * @param after  지웠으면 null
     */
    public record Change(String field, String before, String after) {
    }

    public static NotificationPayload of(String campaignTitle, String shortCode) {
        return new NotificationPayload(campaignTitle, shortCode, List.of(), null);
    }
}
