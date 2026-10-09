package kr.givemeticket.api.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.campaign.application.dto.request.CampaignSearchRequest;
import kr.givemeticket.api.campaign.application.dto.response.CampaignPageResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSearchResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSummaryResponse;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.infrastructure.NoOpCampaignCacheRepository;
import kr.givemeticket.api.user.application.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 검색은 남이 보는 목록이다. 삭제된 행사는 나오지 않고, 끝없이 길어질 수 있어 커서로 끊는다.
 * 필터는 화면의 칩과 같은 규칙이어야 한다 — 칩끼리 OR, 진행중과 매진은 겹치지 않는다.
 * 찜 목록은 내 목록이라 삭제된 행사도 남는다.
 */
class CampaignSearchTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_OWNER_ID = 11L;
    private static final Long USER_ID = 20L;
    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 9, 1, 10, 0);

    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeStockRepository stockRepository = new FakeStockRepository();
    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakeWishRepository wishRepository = new FakeWishRepository();
    private final CampaignCacheRepository noOpCache = new NoOpCampaignCacheRepository();

    private final CampaignService campaignService = new CampaignService(
            campaignRepository, null, null, null, stockRepository, null,
            noOpCache, new CampaignCacheEvictor(noOpCache), null,
            new UserService(userRepository, null), event -> { }, wishRepository);

    @Nested
    @DisplayName("검색어는")
    class Keyword {

        @Test
        @DisplayName("대소문자를 가리지 않고 최신 행사부터 찾는다")
        void findsCaseInsensitivelyLatestFirst() {
            givenCampaign(1L, OWNER_ID, "IU Concert", CampaignStatus.OPEN);
            givenCampaign(2L, OWNER_ID, "뮤지컬", CampaignStatus.OPEN);
            givenCampaign(3L, OWNER_ID, "iu 팬미팅 concert", CampaignStatus.SCHEDULED);

            CampaignSearchResponse page = search(keyword("CONCERT"));

            assertThat(ids(page)).containsExactly(3L, 1L);
            assertThat(page.nextCursor()).isNull();
            assertThat(page.totalCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("비우면 필터만으로 찾는다. 아무 조건이 없으면 삭제되지 않은 행사 전체다")
        void allowsEmptyKeyword() {
            givenCampaign(1L, OWNER_ID, "콘서트", CampaignStatus.OPEN);
            givenCampaign(2L, OWNER_ID, "뮤지컬", CampaignStatus.DELETED);
            givenCampaign(3L, OWNER_ID, "전시", CampaignStatus.CLOSED);

            assertThat(ids(search(keyword("   ")))).containsExactly(3L, 1L);
            assertThat(ids(search(keyword(null)))).containsExactly(3L, 1L);
        }

        @Test
        @DisplayName("너무 긴 검색어는 400 이다")
        void rejectsTooLongKeyword() {
            assertThatThrownBy(() -> search(keyword("가".repeat(CampaignService.KEYWORD_MAX_LENGTH + 1))))
                    .isInstanceOf(CampaignApplicationException.class)
                    .hasMessageContaining("검색어");
        }
    }

    @Nested
    @DisplayName("상태 칩은")
    class StateChips {

        @Test
        @DisplayName("고른 칩끼리 OR 로 묶인다")
        void combinesChipsWithOr() {
            givenCampaign(1L, OWNER_ID, "A", CampaignStatus.OPEN);
            givenCampaign(2L, OWNER_ID, "B", CampaignStatus.SCHEDULED);
            givenCampaign(3L, OWNER_ID, "C", CampaignStatus.CLOSED);

            assertThat(ids(search(statuses(List.of("open", "SCHEDULED"), null)))).containsExactly(2L, 1L);
        }

        @Test
        @DisplayName("진행중은 매진을 빼고, 매진 칩은 진행 중이면서 매진인 행사만 낸다")
        void separatesOpenAndSoldOut() {
            givenCampaign(1L, OWNER_ID, "진행중", CampaignStatus.OPEN);
            givenCampaign(2L, OWNER_ID, "매진", CampaignStatus.OPEN);
            givenCampaign(3L, OWNER_ID, "종료된 매진", CampaignStatus.CLOSED);
            campaignRepository.soldOutIds.addAll(List.of(2L, 3L));

            assertThat(ids(search(statuses(List.of("OPEN"), null)))).containsExactly(1L);
            assertThat(ids(search(statuses(null, true)))).containsExactly(2L);
            assertThat(ids(search(statuses(List.of("OPEN"), true)))).containsExactly(2L, 1L);
            assertThat(ids(search(statuses(List.of("CLOSED"), null)))).containsExactly(3L);
        }

        @Test
        @DisplayName("고를 수 없는 상태는 400 이다 — 삭제된 행사는 검색 대상이 아니다")
        void rejectsUnknownStatus() {
            assertThatThrownBy(() -> search(statuses(List.of("DELETED"), null)))
                    .isInstanceOf(CampaignApplicationException.class);
            assertThatThrownBy(() -> search(statuses(List.of("SOLD_OUT"), null)))
                    .isInstanceOf(CampaignApplicationException.class);
        }
    }

    @Nested
    @DisplayName("오픈 날짜는")
    class OpenDate {

        @Test
        @DisplayName("한국 시간 날짜로 받고 양 끝 날짜를 포함한다")
        void includesBothEndsInKst() {
            // 저장된 openAt 은 UTC 다. 한국 시간 10월 1일 0시 = UTC 9월 30일 15시.
            givenCampaign(1L, OWNER_ID, "9/30 23:59 KST", LocalDateTime.of(2026, 9, 30, 14, 59));
            givenCampaign(2L, OWNER_ID, "10/1 00:00 KST", LocalDateTime.of(2026, 9, 30, 15, 0));
            givenCampaign(3L, OWNER_ID, "10/2 23:59 KST", LocalDateTime.of(2026, 10, 2, 14, 59));
            givenCampaign(4L, OWNER_ID, "10/3 00:00 KST", LocalDateTime.of(2026, 10, 2, 15, 0));

            assertThat(ids(search(openRange("2026-10-01", "2026-10-02")))).containsExactly(3L, 2L);
            assertThat(ids(search(openRange("2026-10-03", null)))).containsExactly(4L);
        }

        @Test
        @DisplayName("형식이 틀리거나 시작이 끝보다 늦으면 400 이다")
        void rejectsInvalidRange() {
            assertThatThrownBy(() -> search(openRange("2026/10/01", null)))
                    .isInstanceOf(CampaignApplicationException.class);
            assertThatThrownBy(() -> search(openRange("2026-10-05", "2026-10-01")))
                    .isInstanceOf(CampaignApplicationException.class);
        }
    }

    @Nested
    @DisplayName("정렬과 페이징은")
    class SortAndPaging {

        @Test
        @DisplayName("최신순 커서로 이어서 읽고, 전체 건수는 페이지와 상관없이 같다")
        void pagesLatestWithCursor() {
            for (long id = 1; id <= 5; id++) {
                givenCampaign(id, OWNER_ID, "콘서트 " + id, CampaignStatus.OPEN);
            }

            CampaignSearchResponse first = search(page(null, null, 2));
            CampaignSearchResponse second = search(page(null, first.nextCursor(), 2));
            CampaignSearchResponse last = search(page(null, second.nextCursor(), 2));

            assertThat(ids(first)).containsExactly(5L, 4L);
            assertThat(first.nextCursor()).isEqualTo("4");
            assertThat(ids(second)).containsExactly(3L, 2L);
            assertThat(ids(last)).containsExactly(1L);
            assertThat(last.nextCursor()).isNull();
            assertThat(List.of(first.totalCount(), second.totalCount(), last.totalCount()))
                    .containsOnly(5L);
        }

        @Test
        @DisplayName("오픈 순 정렬은 같은 시각이면 id 로 가르고, 커서로 빠짐없이 이어진다")
        void pagesByOpenAtWithTies() {
            LocalDateTime base = LocalDateTime.of(2026, 10, 1, 3, 0);
            givenCampaign(1L, OWNER_ID, "A", base.plusHours(2));
            givenCampaign(2L, OWNER_ID, "B", base);
            givenCampaign(3L, OWNER_ID, "C", base);
            givenCampaign(4L, OWNER_ID, "D", base.plusHours(1));

            CampaignSearchResponse first = search(page("openAt,asc", null, 2));
            CampaignSearchResponse second = search(page("openAt,asc", first.nextCursor(), 2));
            assertThat(ids(first)).containsExactly(2L, 3L);
            assertThat(ids(second)).containsExactly(4L, 1L);
            assertThat(second.nextCursor()).isNull();

            CampaignSearchResponse desc = search(page("openAt,DESC", null, 3));
            CampaignSearchResponse descRest = search(page("openAt,desc", desc.nextCursor(), 3));
            assertThat(ids(desc)).containsExactly(1L, 4L, 3L);
            assertThat(ids(descRest)).containsExactly(2L);
        }

        @Test
        @DisplayName("이 API 가 숫자 커서를 쓰던 때의 값도 최신순에서는 그대로 통한다")
        void acceptsLegacyNumericCursor() {
            for (long id = 1; id <= 3; id++) {
                givenCampaign(id, OWNER_ID, "콘서트", CampaignStatus.OPEN);
            }

            assertThat(ids(search(page(null, "3", 20)))).containsExactly(2L, 1L);
        }

        @Test
        @DisplayName("잘못된 정렬·커서·페이지 크기는 400 이다")
        void rejectsInvalidPaging() {
            assertThatThrownBy(() -> search(page("title,asc", null, null)))
                    .isInstanceOf(CampaignApplicationException.class);
            assertThatThrownBy(() -> search(page("openAt,asc", "!!broken", null)))
                    .isInstanceOf(CampaignApplicationException.class);
            assertThatThrownBy(() -> search(page(null, "abc", null)))
                    .isInstanceOf(CampaignApplicationException.class);
            assertThatThrownBy(() -> search(page(null, null, CampaignService.MAX_PAGE_SIZE + 1)))
                    .isInstanceOf(CampaignApplicationException.class);
        }
    }

    private CampaignSearchResponse search(CampaignSearchRequest request) {
        return campaignService.search(request);
    }

    private static CampaignSearchRequest keyword(String keyword) {
        return new CampaignSearchRequest(keyword, null, null, null, null, null, null, null);
    }

    private static CampaignSearchRequest statuses(List<String> statuses, Boolean soldOut) {
        return new CampaignSearchRequest(null, statuses, soldOut, null, null, null, null, null);
    }

    private static CampaignSearchRequest openRange(String from, String to) {
        return new CampaignSearchRequest(null, null, null, from, to, null, null, null);
    }

    private static CampaignSearchRequest page(String sort, String cursor, Integer size) {
        return new CampaignSearchRequest(null, null, null, null, null, sort, cursor, size);
    }

    @Nested
    @DisplayName("작성자 검색은")
    class ByOwner {

        @Test
        @DisplayName("그 사람이 연 행사만, 삭제된 것은 빼고 최신순으로 나온다")
        void findsOwnedLiveCampaigns() {
            givenCampaign(1L, OWNER_ID, "A", CampaignStatus.OPEN);
            givenCampaign(2L, OTHER_OWNER_ID, "B", CampaignStatus.OPEN);
            givenCampaign(3L, OWNER_ID, "C", CampaignStatus.DELETED);
            givenCampaign(4L, OWNER_ID, "D", CampaignStatus.SCHEDULED);

            assertThat(ids(campaignService.getCampaignsOwnedBy(OWNER_ID, null, null)))
                    .containsExactly(4L, 1L);
        }

        @Test
        @DisplayName("연 행사가 없으면 빈 목록이다")
        void returnsEmptyForUnknownOwner() {
            CampaignPageResponse page = campaignService.getCampaignsOwnedBy(999L, null, null);

            assertThat(page.campaigns()).isEmpty();
            assertThat(page.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("찜 목록은")
    class Wished {

        @Test
        @DisplayName("최근에 찜한 행사가 앞이고, 삭제된 행사도 남는다")
        void listsLatestWishFirstIncludingDeleted() {
            givenCampaign(1L, OWNER_ID, "A", CampaignStatus.OPEN);
            givenCampaign(2L, OWNER_ID, "B", CampaignStatus.DELETED);
            givenCampaign(3L, OWNER_ID, "C", CampaignStatus.OPEN);
            wishRepository.put(USER_ID, 3L);
            wishRepository.put(USER_ID, 1L);
            wishRepository.put(USER_ID, 2L);
            wishRepository.put(99L, 3L);

            assertThat(campaignService.getWishedCampaigns(USER_ID))
                    .extracting(summary -> summary.campaign().id())
                    .containsExactly(2L, 1L, 3L);
        }
    }

    private static List<Long> ids(CampaignSearchResponse page) {
        return page.campaigns().stream().map(CampaignSummaryResponse::campaign)
                .map(campaign -> campaign.id()).toList();
    }

    private static List<Long> ids(CampaignPageResponse page) {
        return page.campaigns().stream().map(CampaignSummaryResponse::campaign)
                .map(campaign -> campaign.id()).toList();
    }

    private void givenCampaign(Long campaignId, Long ownerId, String title, LocalDateTime openAt) {
        givenCampaign(campaignId, ownerId, title, CampaignStatus.OPEN, openAt);
    }

    private void givenCampaign(Long campaignId, Long ownerId, String title, CampaignStatus status) {
        givenCampaign(campaignId, ownerId, title, status, OPEN_AT);
    }

    private void givenCampaign(
            Long campaignId, Long ownerId, String title, CampaignStatus status, LocalDateTime openAt) {
        Campaign campaign = new Campaign(
                ownerId, "code" + campaignId, title, CampaignType.TICKET, 10, openAt, null);
        TestEntities.with(campaign, "id", campaignId);
        TestEntities.with(campaign, "status", status);
        campaignRepository.put(campaignId, campaign);
        stockRepository.stock.put(campaignId, 10L);
    }
}
