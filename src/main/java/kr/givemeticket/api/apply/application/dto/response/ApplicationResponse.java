package kr.givemeticket.api.apply.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

/**
 * @param appliedAt 자리를 잡은 시각. 행이 만들어진 시각({@code createdAt})이 아니다 —
 *                  저장이 비동기라 둘은 어긋나고, 취소했다가 다시 신청하면 행을 되쓰기 때문에
 *                  {@code createdAt} 은 <b>처음</b> 신청한 시각에 머문다. 사용자가 보는
 *                  "신청 시각"은 마지막으로 자리를 잡은 시각이어야 한다
 */
public record ApplicationResponse(
        Long id,
        Long campaignId,
        Long userId,
        ApplicationStatus status,
        FailureReason failureReason,
        LocalDateTime appliedAt
) {

    /**
     * 큐에 넣기까지만 끝난 예매. 아직 행이 없지만 상태는 {@code CONFIRMED} 다 —
     * 사용자 입장에서 자리는 이미 잡혔다. 시각은 저장이 끝나야 확정되므로 비운다.
     */
    public static ApplicationResponse accepted(Long id, Long campaignId, Long userId) {
        return new ApplicationResponse(
                id, campaignId, userId, ApplicationStatus.CONFIRMED, null, null);
    }

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCampaignId(),
                application.getUserId(),
                application.getStatus(),
                application.getFailureReason(),
                application.appliedAt()
        );
    }
}
