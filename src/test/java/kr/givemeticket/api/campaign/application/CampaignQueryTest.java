package kr.givemeticket.api.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSummaryResponse;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.infrastructure.NoOpCampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.user.application.UserService;
import kr.givemeticket.api.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 상세·목록 응답은 첫 화면을 한 번에 그릴 수 있어야 한다.
 * 개설자 정보와 잔여 재고가 함께 담기되, 재고를 못 읽어도 조회 자체는 살아 있어야 한다.
 */
class CampaignQueryTest {

    private static final Long OWNER_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 9, 1, 10, 0);

    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeStockRepository stockRepository = new FakeStockRepository();
    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakeApplicationRepository applicationRepository = new FakeApplicationRepository();

    private final CampaignCacheRepository noOpCache = new NoOpCampaignCacheRepository();

    private final CampaignService campaignService = new CampaignService(
            campaignRepository, null, applicationRepository, null, stockRepository, null,
            noOpCache, new CampaignCacheEvictor(noOpCache), null,
            new UserService(userRepository, null));

    @Test
    @DisplayName("목록은 개설자 정보와 잔여 재고를 함께 내려준다")
    void listCarriesOwnerAndStock() {
        givenOwner("민기", "https://img/profile.png");
        givenCampaign(1L, "행사A", CampaignStatus.OPEN, 100, 37L);
        givenCampaign(2L, "행사B", CampaignStatus.SCHEDULED, 50, 50L);

        List<CampaignSummaryResponse> campaigns = campaignService.getOwnedCampaigns(OWNER_ID);

        assertThat(campaigns).hasSize(2);
        assertThat(campaigns.getFirst().owner().nickname()).isEqualTo("민기");
        assertThat(campaigns.getFirst().owner().profileImageUrl()).isEqualTo("https://img/profile.png");
        assertThat(campaigns).extracting(CampaignSummaryResponse::remainingStock)
                .containsExactly(37L, 50L);
    }

    @Test
    @DisplayName("목록의 재고는 캠페인 수와 무관하게 한 번에 읽는다")
    void listReadsStockInOneBatch() {
        givenOwner("민기", null);
        givenCampaign(1L, "행사A", CampaignStatus.OPEN, 100, 37L);
        givenCampaign(2L, "행사B", CampaignStatus.OPEN, 100, 12L);
        givenCampaign(3L, "행사C", CampaignStatus.OPEN, 100, 3L);

        campaignService.getOwnedCampaigns(OWNER_ID);

        assertThat(stockRepository.batchReadCount).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 행사는 재고 키가 없어 remainingStock 이 null 로 내려간다")
    void deletedCampaignHasNullStock() {
        givenOwner("민기", null);
        Campaign deleted = givenCampaign(1L, "지운 행사", CampaignStatus.OPEN, 100, 37L);
        TestEntities.with(deleted, "status", CampaignStatus.DELETED);
        stockRepository.remove(1L);

        List<CampaignSummaryResponse> campaigns = campaignService.getOwnedCampaigns(OWNER_ID);

        assertThat(campaigns.getFirst().campaign().status()).isEqualTo(CampaignStatus.DELETED);
        assertThat(campaigns.getFirst().remainingStock()).isNull();
    }

    @Test
    @DisplayName("재고를 읽지 못해도 목록은 재고만 비운 채 내려간다")
    void listSurvivesStockFailure() {
        givenOwner("민기", null);
        givenCampaign(1L, "행사A", CampaignStatus.OPEN, 100, 37L);
        stockRepository.failing = true;

        List<CampaignSummaryResponse> campaigns = campaignService.getOwnedCampaigns(OWNER_ID);

        assertThat(campaigns).hasSize(1);
        assertThat(campaigns.getFirst().campaign().title()).isEqualTo("행사A");
        assertThat(campaigns.getFirst().remainingStock()).isNull();
    }

    @Test
    @DisplayName("개설자를 찾지 못하면 id 만 채우고 목록은 그대로 내려간다")
    void listSurvivesMissingOwner() {
        givenCampaign(1L, "행사A", CampaignStatus.OPEN, 100, 37L);

        List<CampaignSummaryResponse> campaigns = campaignService.getOwnedCampaigns(OWNER_ID);

        assertThat(campaigns.getFirst().owner().id()).isEqualTo(OWNER_ID);
        assertThat(campaigns.getFirst().owner().nickname()).isNull();
    }

    @Test
    @DisplayName("비로그인 상세 조회에도 개설자 정보와 잔여 재고가 담긴다")
    void detailCarriesOwnerAndStock() {
        givenOwner("민기", "https://img/profile.png");
        givenCampaign(1L, "행사A", CampaignStatus.OPEN, 100, 37L);

        CampaignDetailResponse detail = campaignService.getCampaignDetail("code1", null);

        assertThat(detail.owner().nickname()).isEqualTo("민기");
        assertThat(detail.owner().profileImageUrl()).isEqualTo("https://img/profile.png");
        assertThat(detail.remainingStock()).isEqualTo(37L);
    }

    @Test
    @DisplayName("재고를 읽지 못해도 상세는 재고만 비운 채 내려간다")
    void detailSurvivesStockFailure() {
        givenOwner("민기", null);
        givenCampaign(1L, "행사A", CampaignStatus.OPEN, 100, 37L);
        stockRepository.failing = true;

        CampaignDetailResponse detail = campaignService.getCampaignDetail("code1", null);

        assertThat(detail.campaign().title()).isEqualTo("행사A");
        assertThat(detail.remainingStock()).isNull();
    }

    @Test
    @DisplayName("참여 목록에는 주최자가 지운 행사도 남는다")
    void participatedKeepsDeletedCampaigns() {
        givenOwner("민기", null);
        Campaign live = givenCampaign(1L, "살아있는 행사", CampaignStatus.OPEN, 100, 37L);
        Campaign deleted = givenCampaign(2L, "지워진 행사", CampaignStatus.DELETED, 100, 0L);
        givenApplication(live.getId(), ApplicationStatus.CONFIRMED, null);
        givenApplication(deleted.getId(), ApplicationStatus.CANCELLED, FailureReason.CAMPAIGN_DELETED);

        List<CampaignSummaryResponse> campaigns = campaignService.getParticipatedCampaigns(USER_ID);

        assertThat(campaigns).extracting(summary -> summary.campaign().title())
                .containsExactlyInAnyOrder("살아있는 행사", "지워진 행사");
        assertThat(campaigns)
                .filteredOn(summary -> summary.campaign().status() == CampaignStatus.DELETED)
                .singleElement()
                .extracting(CampaignSummaryResponse::myApplicationStatus)
                .isEqualTo(ApplicationStatus.CANCELLED);
    }

    @Test
    @DisplayName("참여 목록에서 내가 직접 취소한 행사는 빠진다")
    void participatedDropsSelfCancelled() {
        givenOwner("민기", null);
        Campaign campaign = givenCampaign(1L, "내가 취소한 행사", CampaignStatus.OPEN, 100, 38L);
        givenApplication(campaign.getId(), ApplicationStatus.CANCELLED, null);

        assertThat(campaignService.getParticipatedCampaigns(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("종료된 행사는 신청이 그대로라 참여 목록에 남는다")
    void participatedKeepsClosedCampaigns() {
        givenOwner("민기", null);
        Campaign campaign = givenCampaign(1L, "종료된 행사", CampaignStatus.CLOSED, 100, 0L);
        givenApplication(campaign.getId(), ApplicationStatus.CONFIRMED, null);

        List<CampaignSummaryResponse> campaigns = campaignService.getParticipatedCampaigns(USER_ID);

        assertThat(campaigns).singleElement()
                .extracting(summary -> summary.campaign().status())
                .isEqualTo(CampaignStatus.CLOSED);
    }

    private void givenApplication(
            Long campaignId, ApplicationStatus status, FailureReason failureReason) {
        // id 는 이제 채번된 값을 생성자로 받는다. 리플렉션으로 심을 필요가 없다.
        Application application = Application.confirmed(
                campaignId, campaignId, USER_ID, LocalDateTime.now());
        TestEntities.with(application, "status", status);
        TestEntities.with(application, "failureReason", failureReason);

        applicationRepository.put(application);
    }

    private void givenOwner(String nickname, String profileImageUrl) {
        User user = new User(nickname, profileImageUrl, "provider-1", Provider.KAKAO);
        TestEntities.with(user, "id", OWNER_ID);
        userRepository.put(OWNER_ID, user);
    }

    private Campaign givenCampaign(
            Long campaignId, String title, CampaignStatus status, int totalStock, long remaining) {
        Campaign campaign = new Campaign(
                OWNER_ID, "code" + campaignId, title, CampaignType.TICKET,
                totalStock, OPEN_AT, null);
        TestEntities.with(campaign, "id", campaignId);
        TestEntities.with(campaign, "status", status);

        campaignRepository.put(campaignId, campaign);
        stockRepository.stock.put(campaignId, remaining);
        return campaign;
    }
}
