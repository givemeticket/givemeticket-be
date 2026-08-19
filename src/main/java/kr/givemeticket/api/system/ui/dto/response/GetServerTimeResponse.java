package kr.givemeticket.api.system.ui.dto.response;

import java.time.Instant;

/**
 * 프론트가 자기 시계와의 오차를 재는 데 쓴다. 선착순 행사라 "10초 남음" 같은 카운트다운이
 * 사용자 PC 시계에 끌려가면 안 된다.
 *
 * @param serverTime  Z 가 붙은 ISO-8601. {@code new Date(serverTime)} 로 바로 파싱된다
 * @param epochMilli  같은 시각의 epoch milliseconds. 오차 계산에 파싱 없이 바로 쓸 수 있다
 */
public record GetServerTimeResponse(
        Instant serverTime,
        long epochMilli
) {

    public static GetServerTimeResponse of(Instant now) {
        return new GetServerTimeResponse(now, now.toEpochMilli());
    }
}
