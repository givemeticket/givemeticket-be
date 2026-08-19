package kr.givemeticket.api.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
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
        setField(campaign, "id", CAMPAIGN_ID);
        setField(campaign, "status", status);

        campaignRepository.campaigns.put(CAMPAIGN_ID, campaign);
        return campaign;
    }

    private static CampaignUpdateRequest request(LocalDateTime openAt, Integer totalStock) {
        return new CampaignUpdateRequest(openAt, totalStock, null);
    }

    /**
     * 식별자와 상태는 영속화 과정에서 정해지는 값이라 테스트에서 직접 심는다.
     */
    private static void setField(Object target, String name, Object value) {
        try {
            Class<?> type = target.getClass();
            Field field = null;
            while (type != null && field == null) {
                try {
                    field = type.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            if (field == null) {
                throw new NoSuchFieldException(name);
            }
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class FakeCampaignRepository implements CampaignRepository {

        private final Map<Long, Campaign> campaigns = new HashMap<>();

        @Override
        public Optional<Campaign> findById(Long campaignId) {
            return Optional.ofNullable(campaigns.get(campaignId));
        }

        @Override
        public Campaign save(Campaign campaign) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Campaign> findByShortCode(String shortCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByShortCode(String shortCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllOwnedBy(Long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllLiveOwnedBy(Long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllByIdIn(Collection<Long> campaignIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markDeleted(Long campaignId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeStockRepository implements StockRepository {

        private final Map<Long, Long> stock = new HashMap<>();

        @Override
        public void increaseBy(Long campaignId, int delta) {
            stock.merge(campaignId, (long) delta, Long::sum);
        }

        @Override
        public void initialize(Long campaignId, int totalStock) {
            stock.put(campaignId, (long) totalStock);
        }

        @Override
        public StockDecreaseResult decrease(Long campaignId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void restore(Long campaignId, int upperBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long getRemaining(Long campaignId) {
            return stock.get(campaignId);
        }

        @Override
        public void remove(Long campaignId) {
            stock.remove(campaignId);
        }
    }

    private static class FakeCampaignStateRepository implements CampaignStateRepository {

        private final Map<Long, CampaignState> states = new HashMap<>();

        @Override
        public void save(Long campaignId, CampaignState state) {
            states.put(campaignId, state);
        }

        @Override
        public Optional<CampaignState> find(Long campaignId) {
            return Optional.ofNullable(states.get(campaignId));
        }

        @Override
        public void remove(Long campaignId) {
            states.remove(campaignId);
        }
    }
}
