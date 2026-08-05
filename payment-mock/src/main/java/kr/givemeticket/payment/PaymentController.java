package kr.givemeticket.payment;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final FaultProperties fault;

    @PostMapping("/payments")
    public PaymentChargeResponse charge(@RequestBody PaymentChargeRequest request) {
        if (roll() < fault.timeoutRate()) {
            log.warn("injecting timeout ({}ms): applicationId={}", fault.timeoutMs(), request.applicationId());
            sleep(fault.timeoutMs());
        }

        long delay = fault.delayMs();
        if (fault.jitterMs() > 0) {
            delay += ThreadLocalRandom.current().nextLong(fault.jitterMs() + 1);
        }
        sleep(delay);

        if (roll() < fault.errorRate()) {
            log.warn("injecting 500 error: applicationId={}", request.applicationId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "payment gateway error (injected)");
        }

        if (roll() < fault.declineRate()) {
            log.info("injecting decline: applicationId={}", request.applicationId());
            return PaymentChargeResponse.declined();
        }

        String transactionId = "tx_" + UUID.randomUUID();
        log.info("payment approved: applicationId={}, txId={}", request.applicationId(), transactionId);
        return PaymentChargeResponse.approved(transactionId);
    }

    @GetMapping("/fault")
    public FaultProperties currentFault() {
        return fault;
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
