package kr.givemeticket.api.campaign.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.CampaignDetail;

public record CampaignDetailInfo(
        String content,
        LocalDateTime eventAt,
        LocalDateTime eventEndAt,
        String location,
        String address,
        String imageUrl,
        String contact,
        Integer price
) {

    public static CampaignDetailInfo from(CampaignDetail detail) {
        if (detail == null) {
            return null;
        }
        return new CampaignDetailInfo(
                detail.getContent(),
                detail.getEventAt(),
                detail.getEventEndAt(),
                detail.getLocation(),
                detail.getAddress(),
                detail.getImageUrl(),
                detail.getContact(),
                detail.getPrice()
        );
    }
}
