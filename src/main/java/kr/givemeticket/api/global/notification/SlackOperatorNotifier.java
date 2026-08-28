package kr.givemeticket.api.global.notification;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 슬랙 Incoming Webhook 으로 보낸다.
 *
 * <p>웹훅 URL 이 비면 <b>보내지 않고 로그만 남긴다</b> — 설정 없이도 로컬이 돌아야 하고,
 * 설정을 빠뜨렸다고 기동이 깨지는 것보다 낫다. 대신 기동 시 한 번 경고한다.
 */
@Slf4j
@Component
public class SlackOperatorNotifier implements OperatorNotifier {

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackOperatorNotifier(
            @Value("${slack.webhook-url:}") String webhookUrl,
            @Value("${slack.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${slack.read-timeout-ms:3000}") long readTimeoutMs
    ) {
        this.webhookUrl = webhookUrl;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();

        if (webhookUrl.isBlank()) {
            log.warn("slack.webhook-url 이 비어 있습니다. DLQ 알림은 로그로만 남습니다.");
        }
    }

    /** 슬랙으로 보낸다. 실패해도 예외를 밖으로 내지 않는다. */
    @Override
    public void notifyFailure(String title, String detail) {
        String text = ":rotating_light: *" + title + "*\n```" + detail + "```";

        if (webhookUrl.isBlank()) {
            log.error("operator notification (slack disabled): {} | {}", title, detail);
            return;
        }

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            // 알리려다 실패해도 호출자를 깨뜨리지 않는다.
            log.error("slack notification failed: {} | {}", title, detail, e);
        }
    }
}
