package kr.givemeticket.api.campaign.ui.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.request.CampaignCreateRequest;

public record PostCampaignRequest(
        @NotBlank(message = "title은 필수입니다.")
        String title,

        @Positive(message = "totalStock은 1 이상이어야 합니다.")
        int totalStock,

        @NotNull(message = "openAt은 필수입니다.")
        @Future(message = "openAt은 미래 시각이어야 합니다.")
        LocalDateTime openAt,

        @Valid
        CampaignDetailRequest detail
) {

    public CampaignCreateRequest toCampaignCreateRequest() {
        return new CampaignCreateRequest(
                title,
                totalStock,
                openAt,
                CampaignDetailRequest.toCommandOrNull(detail));
    }
}
