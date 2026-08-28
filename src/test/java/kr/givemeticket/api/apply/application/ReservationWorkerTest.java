package kr.givemeticket.api.apply.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.apply.application.ReservationWorker.Disposition;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import kr.givemeticket.api.apply.domain.DeadLetter;
import kr.givemeticket.api.apply.domain.DeadLetterQueue;
import kr.givemeticket.api.apply.domain.ReservationRetryQueue;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
import kr.givemeticket.api.global.notification.OperatorNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 워커가 메시지를 어디로 보내는지를 고정한다.
 *
 * <p>가장 중요한 것은 <b>갈 곳을 마련하지 못했을 때 ack 하지 않는다</b>는 것이다.
 * 그 한 줄이 무너지면 사용자가 201 을 받은 예매가 조용히 사라진다.
 */
class ReservationWorkerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 10, 0);
    private static final ReservationEvent EVENT = ReservationEvent.first(101L, 1L, 7L, NOW);

    private final FakeApplicationRepository repository = new FakeApplicationRepository();
    private final RecordingRetryQueue retryQueue = new RecordingRetryQueue();
    private final RecordingDeadLetterQueue deadLetterQueue = new RecordingDeadLetterQueue();
    private final RecordingStockRepository stockRepository = new RecordingStockRepository();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RetryPolicy policy = new RetryPolicy(
            Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ZERO, 3);

    @Test
    @DisplayName("저장에 성공하면 ack 한다")
    void acknowledgesOnSuccess() {
        ReservationWorker worker = worker(new ApplicationPersister(repository));

        assertThat(worker.handle("1-1", EVENT)).isEqualTo(Disposition.ACKNOWLEDGE);
        assertThat(repository.created).hasSize(1);
        assertThat(retryQueue.scheduled).isEmpty();
    }

    @Test
    @DisplayName("저장에 실패하면 지연 큐로 옮기고 ack 한다 — 양쪽에 남기지 않는다")
    void movesToRetryQueueOnFailure() {
        ReservationWorker worker = worker(failingPersister());

        assertThat(worker.handle("1-1", EVENT)).isEqualTo(Disposition.ACKNOWLEDGE);
        assertThat(retryQueue.scheduled).singleElement().satisfies(entry -> {
            assertThat(entry.event().attempt()).isEqualTo(1);
            assertThat(entry.delay()).isEqualTo(Duration.ofSeconds(2));
        });
    }

    @Test
    @DisplayName("재시도 한도를 넘기면 DLQ 로 격리하고 ack 한다")
    void isolatesWhenExhausted() {
        ReservationWorker worker = worker(failingPersister());
        ReservationEvent exhausted = EVENT.nextAttempt().nextAttempt();

        assertThat(worker.handle("1-1", exhausted)).isEqualTo(Disposition.ACKNOWLEDGE);
        assertThat(retryQueue.scheduled).isEmpty();
        assertThat(deadLetterQueue.isolated).hasSize(1);
    }

    @Test
    @DisplayName("격리해도 재고를 되돌리지 않는다 — 사용자가 이미 성공 응답을 받은 자리다")
    void keepsSeatOnIsolation() {
        ReservationWorker worker = worker(failingPersister());

        worker.handle("1-1", EVENT.nextAttempt().nextAttempt());

        assertThat(deadLetterQueue.isolated).hasSize(1);
        assertThat(stockRepository.restored).isEmpty();
    }

    @Test
    @DisplayName("격리하면 사람에게 알린다 — 자리를 잡고 있다는 사실까지 알려야 한다")
    void notifiesThatSeatIsHeld() {
        ReservationWorker worker = worker(failingPersister());

        worker.handle("1-1", EVENT.nextAttempt().nextAttempt());

        assertThat(notifier.titles).singleElement().asString().contains("재고 점유 중");
    }

    @Test
    @DisplayName("격리에 실패하면 ack 하지 않는다 — 갈 곳이 없는데 빼면 그대로 유실이다")
    void leavesPendingWhenIsolationFails() {
        deadLetterQueue.failing = true;
        ReservationWorker worker = worker(failingPersister());

        assertThat(worker.handle("1-1", EVENT.nextAttempt().nextAttempt()))
                .isEqualTo(Disposition.LEAVE_PENDING);
    }

    @Test
    @DisplayName("지연 큐에 넣지 못해도 ack 하지 않는다")
    void leavesPendingWhenSchedulingFails() {
        retryQueue.failing = true;
        ReservationWorker worker = worker(failingPersister());

        assertThat(worker.handle("1-1", EVENT)).isEqualTo(Disposition.LEAVE_PENDING);
    }

    @Test
    @DisplayName("해석 불가 메시지는 지연 큐를 건너뛰고 곧바로 격리한다")
    void isolatesUndecodableImmediately() {
        ReservationWorker worker = worker(new ApplicationPersister(repository));

        Disposition disposition = worker.handleUndecodable(
                "1-1", Map.of("applicationId", "?"), new IllegalArgumentException("broken"));

        assertThat(disposition).isEqualTo(Disposition.ACKNOWLEDGE);
        assertThat(retryQueue.scheduled).isEmpty();
        assertThat(deadLetterQueue.isolatedRaw).hasSize(1);
        assertThat(notifier.titles).singleElement().asString().contains("해석 실패");
    }

    private ReservationWorker worker(ApplicationPersister persister) {
        return new ReservationWorker(persister, retryQueue,
                new ReservationIsolator(deadLetterQueue, notifier), policy);
    }

    private ApplicationPersister failingPersister() {
        return new ApplicationPersister(repository) {
            @Override
            public void persist(ReservationEvent event) {
                throw new IllegalStateException("deadlock");
            }
        };
    }

    private record Scheduled(ReservationEvent event, Duration delay) { }

    private static final class RecordingDeadLetterQueue implements DeadLetterQueue {
        private final List<ReservationEvent> isolated = new ArrayList<>();
        private final List<String> isolatedRaw = new ArrayList<>();
        private boolean failing;

        @Override
        public void isolate(ReservationEvent event, String reason) {
            if (failing) {
                throw new IllegalStateException("redis down");
            }
            isolated.add(event);
        }

        @Override
        public void isolateRaw(String messageId, Map<String, String> fields, String reason) {
            if (failing) {
                throw new IllegalStateException("redis down");
            }
            isolatedRaw.add(messageId);
        }

        @Override
        public List<DeadLetter> peek(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean requeue(String deadLetterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long size() {
            return isolated.size() + isolatedRaw.size();
        }
    }

    private static final class RecordingStockRepository implements StockRepository {
        private final List<String> restored = new ArrayList<>();

        @Override
        public void restore(Long campaignId, Long userId, int upperBound) {
            restored.add("campaign=%d,user=%d,upperBound=%d".formatted(campaignId, userId, upperBound));
        }

        @Override
        public StockDecreaseResult decrease(Long campaignId, Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void initialize(Long campaignId, int totalStock) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void increaseBy(Long campaignId, int delta) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long getRemaining(Long campaignId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Long, Long> getRemainingAll(Collection<Long> campaignIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Long campaignId) {
            throw new UnsupportedOperationException();
        }
    }


    private static final class RecordingNotifier implements OperatorNotifier {
        private final List<String> titles = new ArrayList<>();

        @Override
        public void notifyFailure(String title, String detail) {
            titles.add(title);
        }
    }

    private static final class RecordingRetryQueue implements ReservationRetryQueue {
        private final List<Scheduled> scheduled = new ArrayList<>();
        private boolean failing;

        @Override
        public void schedule(ReservationEvent event, Duration delay) {
            if (failing) {
                throw new IllegalStateException("redis down");
            }
            scheduled.add(new Scheduled(event, delay));
        }

        @Override
        public long promoteDue() {
            throw new UnsupportedOperationException();
        }
    }
}
