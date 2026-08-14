package kr.givemeticket.api.campaign.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailInfo;
import kr.givemeticket.api.global.time.Utc;

/**
 * 행사 안내 정보. 등록된 게 없으면 응답에서 통째로 null이다.
 *
 * @param eventAt    UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
 * @param eventEndAt 위와 같다
 */
public record CampaignDetailResponsePart(
        String content,
        Instant eventAt,
        Instant eventEndAt,
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
                Utc.toInstant(info.eventAt()),
                Utc.toInstant(info.eventEndAt()),
                info.location(),
                info.address(),
                info.imageUrl(),
                info.contact(),
                info.price()
        );
    }
}
