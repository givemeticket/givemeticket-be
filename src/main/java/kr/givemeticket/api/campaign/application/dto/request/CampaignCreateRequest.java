package kr.givemeticket.api.campaign.application.dto.request;

import java.time.LocalDateTime;

public record CampaignCreateRequest(
        String title,
        int totalStock,
        LocalDateTime openAt
) {
}
