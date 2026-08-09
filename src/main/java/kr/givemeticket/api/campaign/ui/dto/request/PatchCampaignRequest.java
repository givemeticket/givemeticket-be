package kr.givemeticket.api.campaign.ui.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;

/**
 * 전부 선택 항목이다. openAt·totalStock은 지연·증원 방향으로만 바꿀 수 있고,
 * detail은 지정하면 통째로 교체된다.
 */
public record PatchCampaignRequest(
        @Future(message = "openAt은 미래 시각이어야 합니다.")
        LocalDateTime openAt,

        @Positive(message = "totalStock은 1 이상이어야 합니다.")
        Integer totalStock,

        @Valid
        CampaignDetailRequest detail
) {

    public CampaignUpdateRequest toCampaignUpdateRequest() {
        return new CampaignUpdateRequest(
                openAt, totalStock, CampaignDetailRequest.toCommandOrNull(detail));
    }
}
