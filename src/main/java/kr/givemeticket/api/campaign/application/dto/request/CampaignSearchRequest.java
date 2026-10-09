package kr.givemeticket.api.campaign.application.dto.request;

import java.util.List;

/**
 * 검색 요청을 받은 그대로 담는다. 해석과 검증은 서비스가 한다.
 *
 * @param statuses OPEN·SCHEDULED·CLOSED. 대소문자를 가리지 않는다
 * @param soldOut  매진 칩. null 이면 false 와 같다
 * @param openFrom YYYY-MM-DD, 한국 시간. 그날 0시부터 포함
 * @param openTo   YYYY-MM-DD, 한국 시간. 그날 끝까지 포함
 * @param sort     openAt,asc / openAt,desc. 비우면 최신순
 * @param cursor   이전 응답의 nextCursor
 */
public record CampaignSearchRequest(
        String keyword,
        List<String> statuses,
        Boolean soldOut,
        String openFrom,
        String openTo,
        String sort,
        String cursor,
        Integer size
) {
}
