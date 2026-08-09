package kr.givemeticket.api.campaign.ui.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.CampaignDetailCommand;
import kr.givemeticket.api.campaign.domain.CampaignDetail;

/**
 * 행사 안내 정보. 전체를 생략할 수 있고, 각 필드도 전부 선택이다.
 */
public record CampaignDetailRequest(
        @Size(max = CampaignDetail.MAX_CONTENT_LENGTH, message = "content는 5000자를 넘을 수 없습니다.")
        String content,

        LocalDateTime eventAt,

        LocalDateTime eventEndAt,

        @Size(max = 200, message = "location은 200자를 넘을 수 없습니다.")
        String location,

        @Size(max = 300, message = "address는 300자를 넘을 수 없습니다.")
        String address,

        @Size(max = 500, message = "imageUrl은 500자를 넘을 수 없습니다.")
        String imageUrl,

        @Size(max = 200, message = "contact는 200자를 넘을 수 없습니다.")
        String contact,

        @PositiveOrZero(message = "price는 0 이상이어야 합니다.")
        Integer price
) {

    public CampaignDetailCommand toCampaignDetailCommand() {
        return new CampaignDetailCommand(
                content, eventAt, eventEndAt, location, address, imageUrl, contact, price);
    }

    public static CampaignDetailCommand toCommandOrNull(CampaignDetailRequest request) {
        return (request == null) ? null : request.toCampaignDetailCommand();
    }
}
