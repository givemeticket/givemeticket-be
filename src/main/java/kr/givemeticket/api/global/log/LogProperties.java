package kr.givemeticket.api.global.log;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param excludePatterns 요청/응답 로그를 남기지 않을 URI 패턴 (Ant 패턴)
 * @param maskedKeys      값을 마스킹할 JSON 키 (대소문자 무시, 부분 일치)
 * @param maxBodyLength   이 길이를 넘는 body 는 파싱하지 않고 크기만 기록한다
 */
@ConfigurationProperties(prefix = "app.logging")
public record LogProperties(
        List<String> excludePatterns,
        List<String> maskedKeys,
        int maxBodyLength
) {

    private static final List<String> DEFAULT_EXCLUDE_PATTERNS = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/grafana/**",
            "/favicon.ico"
    );

    private static final List<String> DEFAULT_MASKED_KEYS = List.of(
            "password", "passwd", "pwd", "secret", "token", "authorization", "apikey",
            "card", "cvc", "cvv", "expiry", "account", "ssn", "birth",
            "phone", "mobile", "email", "address"
    );

    private static final int DEFAULT_MAX_BODY_LENGTH = 4096;

    public LogProperties {
        excludePatterns = (excludePatterns == null || excludePatterns.isEmpty())
                ? DEFAULT_EXCLUDE_PATTERNS : List.copyOf(excludePatterns);
        maskedKeys = (maskedKeys == null || maskedKeys.isEmpty())
                ? DEFAULT_MASKED_KEYS : List.copyOf(maskedKeys);
        maxBodyLength = (maxBodyLength <= 0) ? DEFAULT_MAX_BODY_LENGTH : maxBodyLength;
    }
}
