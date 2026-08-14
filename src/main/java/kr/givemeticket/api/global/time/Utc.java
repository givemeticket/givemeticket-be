package kr.givemeticket.api.global.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 저장된 시각은 전부 UTC 기준 {@link LocalDateTime} 이다. 응답 DTO 경계에서만 {@link Instant} 로 올려
 * JSON 에 Z 가 붙게 한다. 프론트가 {@code new Date(...)} 로 바로 파싱할 수 있어야 하기 때문이다.
 *
 * <p>UTC 라는 전제는 JVM 타임존에 기대고 있다. 컨테이너는 TZ 를 지정하지 않아 UTC 로 돌지만,
 * 로컬에서 그냥 띄우면 KST 라 값이 어긋난다.
 */
public final class Utc {

    private Utc() {
    }

    /**
     * @param utcDateTime UTC 기준으로 저장된 시각. null 이면 null
     */
    public static Instant toInstant(LocalDateTime utcDateTime) {
        return (utcDateTime == null) ? null : utcDateTime.toInstant(ZoneOffset.UTC);
    }
}
