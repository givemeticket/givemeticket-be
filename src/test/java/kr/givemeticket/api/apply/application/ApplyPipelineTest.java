package kr.givemeticket.api.apply.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationIdIssuer;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.apply.domain.PendingReservation;
import kr.givemeticket.api.apply.domain.PendingReservationStore;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import kr.givemeticket.api.apply.domain.ReservationQueue;
import kr.givemeticket.api.campaign.application.CampaignApplicationException;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * apply 가 큐로 넘어간 뒤의 계약을 고정한다.
 *
 * <p>보는 것은 셋이다 — 사용자가 받는 응답이 예전과 같은가, 저장 대신 큐에 실렸는가,
 * 그리고 <b>실패했을 때 자리를 반드시 되돌리는가</b>. 마지막이 가장 중요하다.
 * 큐에 넣지 못했는데 자리를 잡아둔 채로 두면 아무도 쓸 수 없는 좌석이 생긴다.
 */
class ApplyPipelineTest {

    private static final Long CAMPAIGN_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final int TOTAL_STOCK = 10;

    private final FakeApplicationRepository applicationRepository = new FakeApplicationRepository();
    private final FakeSeatRepository stockRepository = new FakeSeatRepository();
    private final FakeCampaignStateRepository stateRepository = new FakeCampaignStateRepository();
    private final FakeReservationQueue queue = new FakeReservationQueue();
    private final FakePendingStore pendingStore = new FakePendingStore();
    private final CountingIdIssuer idIssuer = new CountingIdIssuer(100L);

    private final ApplicationService service = new ApplicationService(
            applicationRepository,
            null,
            new ApplicationPersister(applicationRepository),
            null,
            stateRepository,
            stockRepository,
            idIssuer,
            queue,
            pendingStore);

    @Test
    @DisplayName("자리를 잡으면 저장을 기다리지 않고 CONFIRMED 로 응답한다")
    void publishesInsteadOfSaving() {
        given();

        ApplicationResponse response = service.apply(CAMPAIGN_ID, USER_ID);

        assertThat(response.status()).isEqualTo(ApplicationStatus.CONFIRMED);
        assertThat(response.id()).isEqualTo(101L);
        assertThat(applicationRepository.created).isEmpty();
        assertThat(queue.published).singleElement()
                .extracting(ReservationEvent::applicationId).isEqualTo(101L);
    }

    @Test
    @DisplayName("조회가 답할 수 있도록 큐에 넣기 전에 대기 레코드를 남긴다")
    void leavesPendingRecord() {
        given();

        service.apply(CAMPAIGN_ID, USER_ID);

        assertThat(pendingStore.find(101L)).contains(
                new PendingReservation(101L, CAMPAIGN_ID, USER_ID));
        assertThat(pendingStore.putOrder).containsExactly("put", "publish");
    }

    @Test
    @DisplayName("워커가 아직 저장하지 않았어도 조회는 CONFIRMED 로 답한다")
    void readsFallBackToPendingRecord() {
        given();
        service.apply(CAMPAIGN_ID, USER_ID);

        ApplicationResponse found = service.getApplication(101L, USER_ID);

        assertThat(found.status()).isEqualTo(ApplicationStatus.CONFIRMED);
        assertThat(found.campaignId()).isEqualTo(CAMPAIGN_ID);
    }

    @Test
    @DisplayName("대기 중인 예매도 남의 번호로는 볼 수 없다")
    void pendingReadStillChecksOwner() {
        given();
        service.apply(CAMPAIGN_ID, USER_ID);

        assertThatThrownBy(() -> service.getApplication(101L, 999L))
                .isInstanceOf(ApplyApplicationException.class);
    }

    @Test
    @DisplayName("이미 신청한 사람은 Redis 단계에서 걸린다 — 재고를 건드리지 않는다")
    void rejectsDuplicateBeforeTouchingStock() {
        given();
        service.apply(CAMPAIGN_ID, USER_ID);
        long remainingAfterFirst = stockRepository.stock.get(CAMPAIGN_ID);

        assertThatThrownBy(() -> service.apply(CAMPAIGN_ID, USER_ID))
                .isInstanceOf(ApplyApplicationException.class);

        assertThat(stockRepository.stock.get(CAMPAIGN_ID)).isEqualTo(remainingAfterFirst);
        assertThat(queue.published).hasSize(1);
    }

    @Test
    @DisplayName("매진이면 큐에 넣지 않는다")
    void rejectsWhenSoldOut() {
        given();
        stockRepository.stock.put(CAMPAIGN_ID, 0L);

        assertThatThrownBy(() -> service.apply(CAMPAIGN_ID, USER_ID))
                .isInstanceOf(CampaignApplicationException.class);

        assertThat(queue.published).isEmpty();
        assertThat(pendingStore.rows).isEmpty();
    }

    @Test
    @DisplayName("큐에 넣지 못하면 잡아둔 자리를 되돌린다 — 아무도 못 쓰는 좌석을 남기지 않는다")
    void restoresSeatWhenPublishFails() {
        given();
        queue.failing = true;

        assertThatThrownBy(() -> service.apply(CAMPAIGN_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(stockRepository.stock.get(CAMPAIGN_ID)).isEqualTo((long) TOTAL_STOCK);
        // 신청자 집합에서도 빠져야 다시 신청할 수 있다.
        assertThat(stockRepository.applicants.getOrDefault(CAMPAIGN_ID, Set.of()))
                .doesNotContain(USER_ID);
    }

    @Test
    @DisplayName("취소됐던 행이 있으면 그 번호를 그대로 쓴다 — 새로 채번하지 않는다")
    void reusesIdOfCancelledRow() {
        given();
        applicationRepository.put(cancelledRow(42L));

        ApplicationResponse response = service.apply(CAMPAIGN_ID, USER_ID);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(idIssuer.issued).isZero();
        assertThat(queue.published).singleElement()
                .extracting(ReservationEvent::applicationId).isEqualTo(42L);
    }

    @Test
    @DisplayName("오픈하지 않은 캠페인은 자리를 잡기 전에 끊는다")
    void rejectsWhenNotOpen() {
        assertThatThrownBy(() -> service.apply(CAMPAIGN_ID, USER_ID))
                .isInstanceOf(CampaignApplicationException.class);

        assertThat(queue.published).isEmpty();
    }

    private void given() {
        stateRepository.states.put(CAMPAIGN_ID, new CampaignState(TOTAL_STOCK));
        stockRepository.stock.put(CAMPAIGN_ID, (long) TOTAL_STOCK);
    }

    private static Application cancelledRow(Long id) {
        Application application = Application.confirmed(id, CAMPAIGN_ID, USER_ID, LocalDateTime.now());
        set(application, "status", ApplicationStatus.CANCELLED);
        set(application, "failureReason", FailureReason.CAMPAIGN_DELETED);
        return application;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 재고와 신청자 집합을 함께 다루는 Lua 스크립트의 규칙을 그대로 흉내낸다. */
    private static final class FakeSeatRepository implements StockRepository {
        final Map<Long, Long> stock = new LinkedHashMap<>();
        final Map<Long, Set<Long>> applicants = new HashMap<>();

        @Override
        public StockDecreaseResult decrease(Long campaignId, Long userId) {
            Long remaining = stock.get(campaignId);
            if (remaining == null) {
                return StockDecreaseResult.notInitialized();
            }
            if (applicants.getOrDefault(campaignId, Set.of()).contains(userId)) {
                return StockDecreaseResult.alreadyApplied();
            }
            if (remaining <= 0) {
                return StockDecreaseResult.soldOut();
            }
            applicants.computeIfAbsent(campaignId, k -> new HashSet<>()).add(userId);
            stock.put(campaignId, remaining - 1);
            return StockDecreaseResult.success(remaining - 1);
        }

        @Override
        public void restore(Long campaignId, Long userId, int upperBound) {
            // SREM 은 상한과 무관하게 언제나 한다.
            applicants.getOrDefault(campaignId, new HashSet<>()).remove(userId);
            Long current = stock.get(campaignId);
            if (current != null && current < upperBound) {
                stock.put(campaignId, current + 1);
            }
        }

        @Override
        public void initialize(Long campaignId, int totalStock) {
            stock.put(campaignId, (long) totalStock);
        }

        @Override
        public void increaseBy(Long campaignId, int delta) {
            stock.merge(campaignId, (long) delta, Long::sum);
        }

        @Override
        public Long getRemaining(Long campaignId) {
            return stock.get(campaignId);
        }

        @Override
        public Map<Long, Long> getRemainingAll(Collection<Long> campaignIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Long campaignId) {
            stock.remove(campaignId);
            applicants.remove(campaignId);
        }
    }

    private static final class FakeCampaignStateRepository implements CampaignStateRepository {
        final Map<Long, CampaignState> states = new LinkedHashMap<>();

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

    private final class FakeReservationQueue implements ReservationQueue {
        private final List<ReservationEvent> published = new ArrayList<>();
        private boolean failing;

        @Override
        public String publish(ReservationEvent event) {
            if (failing) {
                throw new IllegalStateException("queue down");
            }
            pendingStore.putOrder.add("publish");
            published.add(event);
            return "1-" + published.size();
        }
    }

    private static final class FakePendingStore implements PendingReservationStore {
        private final Map<Long, PendingReservation> rows = new LinkedHashMap<>();
        private final List<String> putOrder = new ArrayList<>();

        @Override
        public void put(PendingReservation reservation) {
            putOrder.add("put");
            rows.put(reservation.applicationId(), reservation);
        }

        @Override
        public Optional<PendingReservation> find(Long applicationId) {
            return Optional.ofNullable(rows.get(applicationId));
        }
    }

    private static final class CountingIdIssuer implements ApplicationIdIssuer {
        private long counter;
        private int issued;

        private CountingIdIssuer(long start) {
            this.counter = start;
        }

        @Override
        public long issue() {
            issued++;
            return ++counter;
        }
    }
}
