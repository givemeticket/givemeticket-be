package kr.givemeticket.api.campaign.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.domain.CampaignCursor;
import kr.givemeticket.api.campaign.domain.CampaignSearchCondition;
import kr.givemeticket.api.campaign.domain.CampaignSort;
import kr.givemeticket.api.campaign.domain.CampaignStatus;

/**
 * 검색 조건을 JPQL 의 WHERE·ORDER BY 로 옮긴다. 목록과 전체 건수가 같은 WHERE 를 쓴다.
 *
 * <p>값은 전부 파라미터로 묶는다. 문자열에 이어 붙이는 것은 고정된 조각뿐이다.
 */
final class CampaignSearchQuery {

    /**
     * 매진: 확정 신청 수가 정원에 닿았다. {@code idx_application_campaign_status} 로 행사마다
     * 인덱스 범위만 센다.
     */
    private static final String SOLD_OUT = """
            (SELECT COUNT(a) FROM Application a
              WHERE a.campaignId = c.id AND a.status = :confirmed) >= c.totalStock""";

    private final CampaignSort sort;
    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> parameters = new LinkedHashMap<>();

    private CampaignSearchQuery(CampaignSort sort) {
        this.sort = sort;
    }

    static CampaignSearchQuery of(CampaignSearchCondition condition) {
        CampaignSearchQuery query = new CampaignSearchQuery(condition.sort());

        query.add("c.status <> :deleted", "deleted", CampaignStatus.DELETED);
        if (condition.keyword() != null) {
            // 백슬래시 대신 ! 로 이스케이프한다. MySQL 문자열 리터럴에서 '\' 는 따옴표를 삼켜 버린다.
            query.add("c.title LIKE CONCAT('%', :keyword, '%') ESCAPE '!'",
                    "keyword", escapeLike(condition.keyword()));
        }
        if (condition.openFrom() != null) {
            query.add("c.openAt >= :openFrom", "openFrom", condition.openFrom());
        }
        if (condition.openTo() != null) {
            query.add("c.openAt < :openTo", "openTo", condition.openTo());
        }
        if (condition.filtersState()) {
            query.addStateFilter(condition.statuses(), condition.soldOut());
        }
        return query;
    }

    /** 고른 칩끼리는 OR 다. 진행중은 매진을 뺀 OPEN, 매진은 매진인 OPEN 이다. */
    private void addStateFilter(Set<CampaignStatus> statuses, boolean soldOut) {
        List<String> chips = new ArrayList<>();
        if (statuses.contains(CampaignStatus.SCHEDULED)) {
            chips.add("c.status = :scheduled");
            parameters.put("scheduled", CampaignStatus.SCHEDULED);
        }
        if (statuses.contains(CampaignStatus.CLOSED)) {
            chips.add("c.status = :closed");
            parameters.put("closed", CampaignStatus.CLOSED);
        }
        if (statuses.contains(CampaignStatus.OPEN)) {
            chips.add("(c.status = :open AND NOT " + SOLD_OUT + ")");
        }
        if (soldOut) {
            chips.add("(c.status = :open AND " + SOLD_OUT + ")");
        }
        if (statuses.contains(CampaignStatus.OPEN) || soldOut) {
            parameters.put("open", CampaignStatus.OPEN);
            parameters.put("confirmed", ApplicationStatus.CONFIRMED);
        }
        conditions.add("(" + String.join(" OR ", chips) + ")");
    }

    /**
     * 정렬 순서에서 커서 뒤에 오는 행사만 남긴다. 오픈 시각 정렬은 같은 시각끼리 id 로 가른다.
     */
    CampaignSearchQuery after(CampaignCursor cursor) {
        if (cursor == null) {
            return this;
        }
        switch (sort) {
            case LATEST -> add("c.id < :cursorId", "cursorId", cursor.id());
            case OPEN_AT_ASC -> {
                conditions.add("(c.openAt > :cursorOpenAt OR (c.openAt = :cursorOpenAt AND c.id > :cursorId))");
                parameters.put("cursorOpenAt", cursor.openAt());
                parameters.put("cursorId", cursor.id());
            }
            case OPEN_AT_DESC -> {
                conditions.add("(c.openAt < :cursorOpenAt OR (c.openAt = :cursorOpenAt AND c.id < :cursorId))");
                parameters.put("cursorOpenAt", cursor.openAt());
                parameters.put("cursorId", cursor.id());
            }
        }
        return this;
    }

    String where() {
        return " WHERE " + String.join(" AND ", conditions);
    }

    String orderBy() {
        return switch (sort) {
            case LATEST -> " ORDER BY c.id DESC";
            case OPEN_AT_ASC -> " ORDER BY c.openAt ASC, c.id ASC";
            case OPEN_AT_DESC -> " ORDER BY c.openAt DESC, c.id DESC";
        };
    }

    Map<String, Object> parameters() {
        return parameters;
    }

    /** LIKE 의 와일드카드를 글자로 바꾼다. 이스케이프 문자는 쿼리의 ESCAPE '!' 와 맞춘다. */
    static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private void add(String condition, String name, Object value) {
        conditions.add(condition);
        parameters.put(name, value);
    }
}
