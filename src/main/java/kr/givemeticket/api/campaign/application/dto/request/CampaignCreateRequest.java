package kr.givemeticket.api.campaign.application.dto.request;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.CampaignDetailCommand;

public record CampaignCreateRequest(
        String title,
        int totalStock,
        LocalDateTime openAt,
        CampaignDetailCommand detail
) {
}
