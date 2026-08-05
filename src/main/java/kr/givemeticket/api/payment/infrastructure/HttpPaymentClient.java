package kr.givemeticket.api.payment.infrastructure;

import kr.givemeticket.api.global.log.dto.ErrorLog;
import kr.givemeticket.api.payment.domain.PaymentClient;
import kr.givemeticket.api.payment.domain.PaymentException;
import kr.givemeticket.api.payment.domain.PaymentResult;
import kr.givemeticket.api.payment.infrastructure.dto.PaymentChargeRequest;
import kr.givemeticket.api.payment.infrastructure.dto.PaymentChargeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.Markers;
import org.springframework.http.HttpStatus;
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
            // PaymentException 은 원인 예외를 감싸지 않으므로 실제 원인은 여기서만 남는다.
            ErrorLog errorLog = ErrorLog.externalError(HttpStatus.BAD_GATEWAY.value(), e, "PAYMENT_GATEWAY_ERROR");
            log.error(Markers.appendEntries(errorLog.fields()), errorLog.summary(), e);
            throw PaymentException.gatewayError();
        }
    }
}
