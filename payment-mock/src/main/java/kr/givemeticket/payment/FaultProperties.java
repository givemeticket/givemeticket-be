package kr.givemeticket.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment")
public record FaultProperties(
        long delayMs,
        long jitterMs,
        double errorRate,
        double timeoutRate,
        long timeoutMs,
        double declineRate
) {

    public FaultProperties {
        if (timeoutMs <= 0) {
            timeoutMs = 30_000;
        }
    }
}
