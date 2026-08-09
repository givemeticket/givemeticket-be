package kr.givemeticket.api.campaign.application.dto.request;

import java.time.LocalDateTime;

/**
 * 둘 다 선택 항목이다. 지정된 것만 바뀐다.
 */
public record CampaignUpdateRequest(
        LocalDateTime openAt,
        Integer totalStock
) {

    public boolean isEmpty() {
        return openAt == null && totalStock == null;
    }
}
