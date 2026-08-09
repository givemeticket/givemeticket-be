package kr.givemeticket.api.campaign.application.dto;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.domain.CampaignDetail;

/**
 * 행사 안내 정보 입력값. 전체도, 각 필드도 선택이다.
 */
public record CampaignDetailCommand(
        String content,
        LocalDateTime eventAt,
        LocalDateTime eventEndAt,
        String location,
        String address,
        String imageUrl,
        String contact,
        Integer price
) {

    public CampaignDetail toCampaignDetail() {
        return new CampaignDetail(
                content, eventAt, eventEndAt, location, address, imageUrl, contact, price);
    }

    public static CampaignDetail toCampaignDetailOrNull(CampaignDetailCommand command) {
        if (command == null) {
            return null;
        }
        CampaignDetail detail = command.toCampaignDetail();
        return detail.isEmpty() ? null : detail;
    }
}
