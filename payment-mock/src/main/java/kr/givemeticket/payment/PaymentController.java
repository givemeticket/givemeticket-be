package kr.givemeticket.payment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final FaultProperties fault;

    /** 멱등키 -> 결제 상태. mock이라 인메모리다. 재시작하면 사라진다. */
    private final Map<String, StoredPayment> payments = new ConcurrentHashMap<>();

    @PostMapping("/payments")
    public PaymentChargeResponse charge(@RequestBody PaymentChargeRequest request) {
        String paymentKey = request.paymentKey();
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentKey is required");
        }

        // 키를 먼저 선점한다. 같은 키로 다시 들어오면 결제를 새로 만들지 않고 기존 결과를 돌려준다.
        // 앞선 요청이 아직 처리 중이면 PROCESSING이 나가고, 호출자는 이를 "결과 불명"으로 다룬다.
        StoredPayment existing = payments.putIfAbsent(paymentKey, StoredPayment.processing());
        if (existing != null) {
            log.info("idempotent replay: paymentKey={}, status={}", paymentKey, existing.status());
            return PaymentChargeResponse.from(existing);
        }

        try {
            StoredPayment result = process(request, paymentKey);
            payments.put(paymentKey, result);
            return PaymentChargeResponse.from(result);
        } catch (RuntimeException e) {
            // 처리에 실패했으면 선점을 풀어 재시도가 가능하게 한다.
            payments.remove(paymentKey);
            throw e;
        }
    }

    @GetMapping("/payments/{paymentKey}")
    public PaymentChargeResponse findPayment(@PathVariable String paymentKey) {
        StoredPayment stored = payments.get(paymentKey);
        if (stored == null) {
            // 키가 없다 = 결제 요청이 처리되지 않았다. 정산 배치가 이걸 근거로 실패 확정한다.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown paymentKey");
        }
        return PaymentChargeResponse.from(stored);
    }

    @PostMapping("/payments/{paymentKey}/cancel")
    public PaymentChargeResponse cancel(@PathVariable String paymentKey) {
        StoredPayment stored = payments.get(paymentKey);
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown paymentKey");
        }
        if (stored.isCancelled()) {
            log.info("already cancelled: paymentKey={}", paymentKey);
            return PaymentChargeResponse.from(stored);
        }
        if (!stored.isApproved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "cannot cancel payment in status " + stored.status());
        }
        if (roll() < fault.cancelErrorRate()) {
            log.warn("injecting cancel failure: paymentKey={}", paymentKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "payment cancel error (injected)");
        }

        StoredPayment cancelled = stored.cancel();
        payments.put(paymentKey, cancelled);
        log.info("payment cancelled: paymentKey={}, txId={}", paymentKey, cancelled.transactionId());
        return PaymentChargeResponse.from(cancelled);
    }

    @GetMapping("/fault")
    public FaultProperties currentFault() {
        return fault;
    }

    private StoredPayment process(PaymentChargeRequest request, String paymentKey) {
        if (roll() < fault.timeoutRate()) {
            // 호출자는 read timeout으로 포기하지만 여기서는 결제가 계속 진행돼 승인까지 간다.
            // "PG는 승인했는데 우리는 모르는" 상황을 만들기 위한 것이다.
            log.warn("injecting timeout ({}ms): paymentKey={}", fault.timeoutMs(), paymentKey);
            sleep(fault.timeoutMs());
        }

        long delay = fault.delayMs();
        if (fault.jitterMs() > 0) {
            delay += ThreadLocalRandom.current().nextLong(fault.jitterMs() + 1);
        }
        sleep(delay);

        if (roll() < fault.errorRate()) {
            log.warn("injecting 500 error: paymentKey={}", paymentKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "payment gateway error (injected)");
        }

        if (roll() < fault.declineRate()) {
            log.info("injecting decline: paymentKey={}", paymentKey);
            return StoredPayment.declined();
        }

        String transactionId = "tx_" + UUID.randomUUID();
        log.info("payment approved: applicationId={}, paymentKey={}, txId={}",
                request.applicationId(), paymentKey, transactionId);
        return StoredPayment.approved(transactionId);
    }

    private double roll() {
        return ThreadLocalRandom.current().nextDouble();
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
