package kr.givemeticket.api.payment.infrastructure;

import kr.givemeticket.api.payment.domain.PaymentClient;
import kr.givemeticket.api.payment.domain.PaymentException;
import kr.givemeticket.api.payment.domain.PaymentResult;
import kr.givemeticket.api.payment.infrastructure.dto.PaymentChargeRequest;
import kr.givemeticket.api.payment.infrastructure.dto.PaymentChargeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpPaymentClient implements PaymentClient {

    private final RestClient paymentRestClient;

    @Override
    public PaymentResult charge(Long applicationId, Long userId) {
        try {
            PaymentChargeResponse response = paymentRestClient.post()
                    .uri("/payments")
                    .body(new PaymentChargeRequest(applicationId, userId))
                    .retrieve()
                    .body(PaymentChargeResponse.class);

            if (response == null) {
                throw PaymentException.gatewayError();
            }
            return response.isApproved()
                    ? PaymentResult.approved(response.transactionId())
                    : PaymentResult.declined();
        } catch (RestClientException e) {
            log.warn("payment gateway call failed: applicationId={}, cause={}", applicationId, e.toString());
            throw PaymentException.gatewayError();
        }
    }
}
