package kr.givemeticket.api.campaign.ui.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;

/**
 * 전부 선택 항목이다. 이미 오픈된 행사만 openAt·totalStock 이 지연·증원 방향으로 제한되고,
 * 오픈 전 행사는 자유롭게 바꿀 수 있다. detail은 지정하면 통째로 교체된다.
 *
 * <p>지금과 같은 값을 보내도 오류가 아니다. 폼 전체를 그대로 보내도 안 바꾼 필드는 그냥 지나간다.
 *
 * <p>openAt 에 {@code @Future} 를 걸지 않는다. 이미 오픈된 행사의 openAt 은 과거라서,
 * 폼이 지금 값을 그대로 되돌려 보내는 것만으로 요청 전체가 막힌다. "미래여야 한다"는 제약은
 * 값이 실제로 바뀔 때만 의미가 있으므로 {@code CampaignService} 가 본다.
 */
public record PatchCampaignRequest(
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
