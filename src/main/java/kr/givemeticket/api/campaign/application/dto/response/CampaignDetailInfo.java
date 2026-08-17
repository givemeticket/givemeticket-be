package kr.givemeticket.api.campaign.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.CampaignDetail;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;

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

    /** 캐시에서 꺼낸 값으로 응답을 만들 때 쓴다. */
    public static CampaignDetailInfo from(CampaignSnapshot.Detail detail) {
        if (detail == null) {
            return null;
        }
        return new CampaignDetailInfo(
                detail.content(),
                detail.eventAt(),
                detail.eventEndAt(),
                detail.location(),
                detail.address(),
                detail.imageUrl(),
                detail.contact(),
                detail.price()
        );
    }
}
