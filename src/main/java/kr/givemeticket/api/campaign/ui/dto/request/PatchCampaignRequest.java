package kr.givemeticket.api.campaign.ui.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;

/**
 * 둘 다 선택 항목이지만, 지정되는 경우 지연·증원 방향으로만 가능하다.
 */
public record PatchCampaignRequest(
        @Future(message = "openAt은 미래 시각이어야 합니다.")
        LocalDateTime openAt,

        @Positive(message = "totalStock은 1 이상이어야 합니다.")
        Integer totalStock
) {

    public CampaignUpdateRequest toCampaignUpdateRequest() {
        return new CampaignUpdateRequest(openAt, totalStock);
    }
}
