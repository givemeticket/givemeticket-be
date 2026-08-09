package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailInfo;

/**
 * 행사 안내 정보. 등록된 게 없으면 응답에서 통째로 null이다.
 */
public record CampaignDetailResponsePart(
        String content,
        LocalDateTime eventAt,
        LocalDateTime eventEndAt,
        String location,
        String address,
        String imageUrl,
        String contact,
        Integer price
) {

    public static CampaignDetailResponsePart from(CampaignDetailInfo info) {
        if (info == null) {
            return null;
        }
        return new CampaignDetailResponsePart(
                info.content(),
                info.eventAt(),
                info.eventEndAt(),
                info.location(),
                info.address(),
                info.imageUrl(),
                info.contact(),
                info.price()
        );
    }
}
