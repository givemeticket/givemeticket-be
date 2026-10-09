package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 행사 검색 조건. 삭제된 행사는 조건과 상관없이 늘 빠진다.
 *
 * <p>상태 조건은 화면의 칩(진행중·예정·종료·매진)과 같다. 고른 칩끼리는 <b>OR</b> 다. 진행중과 매진은
 * 겹치지 않는다 — 진행중은 OPEN 이면서 매진이 아닌 행사, 매진은 OPEN 이면서 매진인 행사다.
 * 종료(CLOSED)된 행사는 매진이었어도 종료로만 센다.
 *
 * @param keyword  제목에 들어가야 하는 글자. null 이면 제목 조건이 없다
 * @param statuses OPEN·SCHEDULED·CLOSED 중 고른 것. 비어 있고 soldOut 도 false 면 상태 조건이 없다
 * @param soldOut  매진 칩을 골랐는지
 * @param openFrom 오픈 시각 하한(포함, UTC). null 이면 없다
 * @param openTo   오픈 시각 상한(미포함, UTC). null 이면 없다
 */
public record CampaignSearchCondition(
        String keyword,
        Set<CampaignStatus> statuses,
        boolean soldOut,
        LocalDateTime openFrom,
        LocalDateTime openTo,
        CampaignSort sort
) {

    public CampaignSearchCondition {
        statuses = Set.copyOf(statuses);
        if (statuses.contains(CampaignStatus.DELETED)) {
            throw new IllegalArgumentException("삭제된 행사는 검색 대상이 아니다");
        }
    }

    public boolean filtersState() {
        return !statuses.isEmpty() || soldOut;
    }
}
