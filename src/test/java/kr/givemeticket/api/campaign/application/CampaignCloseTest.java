package kr.givemeticket.api.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 종료는 신규 신청만 막는다. 삭제와 달리 이미 확정된 신청은 건드리지 않는다.
 */
class CampaignCloseTest {

    private static final Long CAMPAIGN_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 9, 1, 10, 0);

    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeStockRepository stockRepository = new FakeStockRepository();
    private final FakeCampaignStateRepository stateRepository = new FakeCampaignStateRepository();

    private final CampaignService campaignService = new CampaignService(
            campaignRepository, null, null, null, stockRepository, stateRepository, null, null);

    @Test
    @DisplayName("종료하면 상태가 CLOSED 가 되고 신청 게이트가 사라진다")
    void closesCampaign() {
        Campaign campaign = given(CampaignStatus.OPEN);
        stateRepository.states.put(CAMPAIGN_ID, new CampaignState(false, 100));

        campaignService.closeCampaign(CAMPAIGN_ID, OWNER_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CLOSED);
        assertThat(stateRepository.states).isEmpty();
    }

    @Test
    @DisplayName("종료해도 잔여 재고는 그대로 남는다")
    void keepsStock() {
        given(CampaignStatus.OPEN);
        stockRepository.stock.put(CAMPAIGN_ID, 37L);

        campaignService.closeCampaign(CAMPAIGN_ID, OWNER_ID);

        assertThat(stockRepository.getRemaining(CAMPAIGN_ID)).isEqualTo(37L);
    }

    @Test
    @DisplayName("오픈 전 행사도 종료할 수 있다")
    void closesScheduledCampaign() {
        Campaign campaign = given(CampaignStatus.SCHEDULED);

        campaignService.closeCampaign(CAMPAIGN_ID, OWNER_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CLOSED);
    }

    @Test
    @DisplayName("두 번 눌러도 결과는 같다")
    void isIdempotent() {
        Campaign campaign = given(CampaignStatus.OPEN);

        campaignService.closeCampaign(CAMPAIGN_ID, OWNER_ID);
        campaignService.closeCampaign(CAMPAIGN_ID, OWNER_ID);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CLOSED);
    }

    @Test
    @DisplayName("개설자가 아니면 종료할 수 없다")
    void rejectsNonOwner() {
        given(CampaignStatus.OPEN);

        assertThatThrownBy(() -> campaignService.closeCampaign(CAMPAIGN_ID, 999L))
                .isInstanceOf(CampaignApplicationException.class)
                .hasMessageContaining("본인이 만든 캠페인만");
    }

    @Test
    @DisplayName("종료된 행사는 오픈 시각을 미뤄 다시 열 수 없다")
    void rejectsRescheduleAfterClose() {
        given(CampaignStatus.CLOSED);

        assertThatThrownBy(() -> campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID,
                new CampaignUpdateRequest(OPEN_AT.plusDays(1), null, null)))
                .isInstanceOf(CampaignApplicationException.class)
                .hasMessageContaining("종료된 행사");
    }

    private Campaign given(CampaignStatus status) {
        Campaign campaign = new Campaign(
                OWNER_ID, "3AbCdEfGh1", "테스트 행사", CampaignType.TICKET,
                100, OPEN_AT, false, null);
        TestEntities.with(campaign, "id", CAMPAIGN_ID);
        TestEntities.with(campaign, "status", status);

        campaignRepository.put(CAMPAIGN_ID, campaign);
        return campaign;
    }
}
