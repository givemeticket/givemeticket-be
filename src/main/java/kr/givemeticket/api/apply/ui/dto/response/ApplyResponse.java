package kr.givemeticket.api.apply.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.global.time.Utc;

/**
 * @param expiresAt PENDING일 때만 채워진다. 이 시각까지 confirm하지 않으면 자리가 회수된다.
 *                  UTC. 프론트가 남은 시간을 세려면 Z가 붙어 있어야 한다
 */
public record ApplyResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        Instant expiresAt
) {

    public static ApplyResponse from(ApplicationResponse response) {
        return new ApplyResponse(
                response.id(),
                response.campaignId(),
                response.userId(),
                response.status(),
                Utc.toInstant(response.expiresAt())
        );
    }
}
