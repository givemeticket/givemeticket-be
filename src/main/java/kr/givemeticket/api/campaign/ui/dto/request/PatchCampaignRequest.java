package kr.givemeticket.api.campaign.ui.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;

/**
 * 전부 선택 항목이다. 이미 오픈된 행사만 openAt·totalStock 이 지연·증원 방향으로 제한되고,
 * 오픈 전 행사는 자유롭게 바꿀 수 있다. detail은 지정하면 통째로 교체된다.
 *
 * <p>지금과 같은 값을 보내도 오류가 아니다. 폼 전체를 그대로 보내도 안 바꾼 필드는 그냥 지나간다.
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
