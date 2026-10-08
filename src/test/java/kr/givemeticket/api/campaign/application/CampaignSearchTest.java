package kr.givemeticket.api.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.campaign.application.dto.response.CampaignPageResponse;
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
    @DisplayName("제목 검색은")
    class ByTitle {

        @Test
        @DisplayName("대소문자를 가리지 않고 최신 행사부터 찾는다")
        void findsCaseInsensitivelyLatestFirst() {
            givenCampaign(1L, OWNER_ID, "IU Concert", CampaignStatus.OPEN);
            givenCampaign(2L, OWNER_ID, "뮤지컬", CampaignStatus.OPEN);
            givenCampaign(3L, OWNER_ID, "iu 팬미팅 concert", CampaignStatus.SCHEDULED);

            CampaignPageResponse page = campaignService.searchByTitle("CONCERT", null, null);

            assertThat(ids(page)).containsExactly(3L, 1L);
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        @DisplayName("앞뒤 공백은 떼고 찾는다")
        void trimsKeyword() {
            givenCampaign(1L, OWNER_ID, "콘서트", CampaignStatus.OPEN);

            assertThat(ids(campaignService.searchByTitle("  콘서트 ", null, null))).containsExactly(1L);
        }

        @Test
        @DisplayName("삭제된 행사는 나오지 않는다. 종료된 행사는 나온다")
        void excludesDeleted() {
            givenCampaign(1L, OWNER_ID, "콘서트 A", CampaignStatus.DELETED);
            givenCampaign(2L, OWNER_ID, "콘서트 B", CampaignStatus.CLOSED);

            assertThat(ids(campaignService.searchByTitle("콘서트", null, null))).containsExactly(2L);
        }

        @Test
        @DisplayName("커서로 이어서 읽는다")
        void pagesWithCursor() {
            for (long id = 1; id <= 5; id++) {
                givenCampaign(id, OWNER_ID, "콘서트 " + id, CampaignStatus.OPEN);
            }

            CampaignPageResponse first = campaignService.searchByTitle("콘서트", null, 2);
            CampaignPageResponse second = campaignService.searchByTitle("콘서트", first.nextCursor(), 2);
            CampaignPageResponse last = campaignService.searchByTitle("콘서트", second.nextCursor(), 2);

            assertThat(ids(first)).containsExactly(5L, 4L);
            assertThat(ids(second)).containsExactly(3L, 2L);
            assertThat(ids(last)).containsExactly(1L);
            assertThat(last.nextCursor()).isNull();
        }

        @Test
        @DisplayName("빈 검색어나 너무 긴 검색어는 400 이다")
        void rejectsInvalidKeyword() {
            assertThatThrownBy(() -> campaignService.searchByTitle("   ", null, null))
                    .isInstanceOf(CampaignApplicationException.class)
                    .hasMessageContaining("검색어");
            assertThatThrownBy(() -> campaignService.searchByTitle(
                    "가".repeat(CampaignService.KEYWORD_MAX_LENGTH + 1), null, null))
                    .isInstanceOf(CampaignApplicationException.class);
        }

        @Test
        @DisplayName("페이지 크기가 범위를 벗어나면 400 이다")
        void rejectsInvalidSize() {
            assertThatThrownBy(() -> campaignService.searchByTitle("콘서트", null, 0))
                    .isInstanceOf(CampaignApplicationException.class);
            assertThatThrownBy(() -> campaignService.searchByTitle(
                    "콘서트", null, CampaignService.MAX_PAGE_SIZE + 1))
                    .isInstanceOf(CampaignApplicationException.class);
        }
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

    private static List<Long> ids(CampaignPageResponse page) {
        return page.campaigns().stream().map(CampaignSummaryResponse::campaign)
                .map(campaign -> campaign.id()).toList();
    }

    private void givenCampaign(Long campaignId, Long ownerId, String title, CampaignStatus status) {
        Campaign campaign = new Campaign(
                ownerId, "code" + campaignId, title, CampaignType.TICKET, 10, OPEN_AT, null);
        TestEntities.with(campaign, "id", campaignId);
        TestEntities.with(campaign, "status", status);
        campaignRepository.put(campaignId, campaign);
        stockRepository.stock.put(campaignId, 10L);
    }
}
