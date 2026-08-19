package kr.givemeticket.api.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 수정 제한은 이미 오픈된 행사에만 걸린다. 오픈 전 행사는 신청자가 없으니 자유롭게 고칠 수 있다.
 */
class CampaignUpdateTest {

    private static final Long CAMPAIGN_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 9, 1, 10, 0);

    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeStockRepository stockRepository = new FakeStockRepository();
    private final FakeCampaignStateRepository stateRepository = new FakeCampaignStateRepository();

    private final CampaignService campaignService = new CampaignService(
            campaignRepository, null, null, null, stockRepository, stateRepository, null, null);

    @Nested
    @DisplayName("오픈 전 행사는")
    class Scheduled {

        @Test
        @DisplayName("오픈 시각을 앞당길 수 있다")
        void allowsEarlierOpenAt() {
            Campaign campaign = given(CampaignStatus.SCHEDULED, 100);

            campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID,
                    request(OPEN_AT.minusDays(1), null));

            assertThat(campaign.getOpenAt()).isEqualTo(OPEN_AT.minusDays(1));
            assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        }

        @Test
        @DisplayName("정원을 줄일 수 있고 재고도 함께 줄어든다")
        void allowsSmallerTotalStock() {
            Campaign campaign = given(CampaignStatus.SCHEDULED, 100);
            stockRepository.stock.put(CAMPAIGN_ID, 100L);

            campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID, request(null, 40));

            assertThat(campaign.getTotalStock()).isEqualTo(40);
            assertThat(stockRepository.stock).containsEntry(CAMPAIGN_ID, 40L);
        }

        @Test
        @DisplayName("바꾸지 않은 오픈 시각을 그대로 같이 보내도 정원만 바뀐다")
        void ignoresUnchangedOpenAt() {
            Campaign campaign = given(CampaignStatus.SCHEDULED, 100);
            stockRepository.stock.put(CAMPAIGN_ID, 100L);

            campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID, request(OPEN_AT, 150));

            assertThat(campaign.getTotalStock()).isEqualTo(150);
            assertThat(campaign.getOpenAt()).isEqualTo(OPEN_AT);
        }
    }

    @Nested
    @DisplayName("이미 오픈된 행사는")
    class Opened {

        @Test
        @DisplayName("오픈 시각을 미루면 접수가 멈추고 오픈 전으로 돌아간다")
        void reschedulesOnDelay() {
            Campaign campaign = given(CampaignStatus.OPEN, 100);
            stateRepository.states.put(CAMPAIGN_ID, new CampaignState(false, 100));

            campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID,
                    request(OPEN_AT.plusHours(2), null));

            assertThat(campaign.getOpenAt()).isEqualTo(OPEN_AT.plusHours(2));
            assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
            assertThat(stateRepository.states).isEmpty();
        }

        @Test
        @DisplayName("오픈 시각을 앞당기면 409다")
        void rejectsEarlierOpenAt() {
            given(CampaignStatus.OPEN, 100);

            assertThatThrownBy(() -> campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID,
                    request(OPEN_AT.minusHours(1), null)))
                    .isInstanceOf(CampaignApplicationException.class)
                    .hasMessageContaining("늦은 시각으로만");
        }

        @Test
        @DisplayName("정원을 줄이면 409다")
        void rejectsSmallerTotalStock() {
            given(CampaignStatus.OPEN, 100);

            assertThatThrownBy(() -> campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID,
                    request(null, 50)))
                    .isInstanceOf(CampaignApplicationException.class)
                    .hasMessageContaining("늘리는 것만");
        }

        @Test
        @DisplayName("오픈 시각을 그대로 둔 채 증원만 해도 통과한다")
        void allowsIncreaseWithUnchangedOpenAt() {
            Campaign campaign = given(CampaignStatus.OPEN, 100);
            stockRepository.stock.put(CAMPAIGN_ID, 0L);
            stateRepository.states.put(CAMPAIGN_ID, new CampaignState(true, 100));

            assertThatCode(() -> campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID,
                    request(OPEN_AT, 130))).doesNotThrowAnyException();

            assertThat(campaign.getTotalStock()).isEqualTo(130);
            assertThat(stockRepository.stock).containsEntry(CAMPAIGN_ID, 30L);
            assertThat(stateRepository.states.get(CAMPAIGN_ID))
                    .isEqualTo(new CampaignState(true, 130));
        }

        @Test
        @DisplayName("오픈 시각을 미루면서 정원을 줄이는 것은 여전히 막힌다")
        void rejectsShrinkEvenWhenDelayed() {
            given(CampaignStatus.OPEN, 100);
            stateRepository.states.put(CAMPAIGN_ID, new CampaignState(false, 100));

            assertThatThrownBy(() -> campaignService.updateCampaign(CAMPAIGN_ID, OWNER_ID,
                    request(OPEN_AT.plusHours(2), 50)))
                    .isInstanceOf(CampaignApplicationException.class)
                    .hasMessageContaining("늘리는 것만");
        }
    }

    private Campaign given(CampaignStatus status, int totalStock) {
        Campaign campaign = new Campaign(
                OWNER_ID, "3AbCdEfGh1", "테스트 행사", CampaignType.TICKET,
                totalStock, OPEN_AT, false, null);
        TestEntities.with(campaign, "id", CAMPAIGN_ID);
        TestEntities.with(campaign, "status", status);

        campaignRepository.put(CAMPAIGN_ID, campaign);
        return campaign;
    }

    private static CampaignUpdateRequest request(LocalDateTime openAt, Integer totalStock) {
        return new CampaignUpdateRequest(openAt, totalStock, null);
    }

}
