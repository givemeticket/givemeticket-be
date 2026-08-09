package kr.givemeticket.api.payment.infrastructure;

import java.net.ConnectException;
import kr.givemeticket.api.global.log.dto.ErrorLog;
import kr.givemeticket.api.payment.domain.PaymentClient;
import kr.givemeticket.api.payment.domain.PaymentResult;
import kr.givemeticket.api.payment.infrastructure.dto.PaymentChargeRequest;
import kr.givemeticket.api.payment.infrastructure.dto.PaymentChargeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.Markers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpPaymentClient implements PaymentClient {

    private final RestClient paymentRestClient;

    @Override
    public PaymentResult charge(String paymentKey, Long applicationId, Long userId) {
        try {
            PaymentChargeResponse response = paymentRestClient.post()
                    .uri("/payments")
                    .body(new PaymentChargeRequest(paymentKey, applicationId, userId))
                    .retrieve()
                    .body(PaymentChargeResponse.class);

            if (response == null) {
                // 200을 받았는데 본문을 해석할 수 없다. 승인 여부를 알 수 없으므로 실패로 단정하지 않는다.
                logGatewayFailure(paymentKey, "PAYMENT_EMPTY_RESPONSE", null);
                return PaymentResult.unknown();
            }
            return response.isApproved()
                    ? PaymentResult.approved(response.transactionId())
                    : PaymentResult.declined();

        } catch (HttpClientErrorException e) {
            // 4xx: 우리 요청이 잘못됐다. 결제는 일어나지 않았다.
            logGatewayFailure(paymentKey, "PAYMENT_BAD_REQUEST", e);
            return PaymentResult.error();

        } catch (HttpServerErrorException e) {
            // 5xx: mock은 승인 처리 전에 실패시킨다. 실제 PG로 바꿀 때는 이 가정이 유효한지 다시 봐야 한다.
            logGatewayFailure(paymentKey, "PAYMENT_GATEWAY_ERROR", e);
            return PaymentResult.error();

        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof ConnectException) {
                // 연결 자체가 안 됐다. 요청이 PG에 닿지 않은 것이 확실하다.
                logGatewayFailure(paymentKey, "PAYMENT_CONNECT_FAILED", e);
                return PaymentResult.error();
            }
            // read timeout. 요청은 갔고 응답만 못 받았다 — 여기서 실패로 단정하면 돈만 빠져나간다.
            logGatewayFailure(paymentKey, "PAYMENT_TIMEOUT", e);
            return PaymentResult.unknown();

        } catch (RestClientException e) {
            logGatewayFailure(paymentKey, "PAYMENT_UNKNOWN_ERROR", e);
            return PaymentResult.unknown();
        }
    }

    private void logGatewayFailure(String paymentKey, String code, Exception e) {
        ErrorLog errorLog = (e == null)
                ? ErrorLog.externalError(HttpStatus.BAD_GATEWAY.value(),
                        new IllegalStateException("empty payment response"), code)
                : ErrorLog.externalError(HttpStatus.BAD_GATEWAY.value(), e, code);

        log.error(Markers.appendEntries(errorLog.fields())
                        .and(Markers.append("payment_key", paymentKey)),
                errorLog.summary(), e);
    }
}
