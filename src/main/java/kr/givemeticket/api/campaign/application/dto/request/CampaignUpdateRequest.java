package kr.givemeticket.api.campaign.application.dto.request;

import java.time.LocalDateTime;
import kr.givemeticket.api.campaign.application.dto.CampaignDetailCommand;

/**
 * 전부 선택 항목이다. 지정된 것만 바뀐다.
 *
 * @param detail 지정하면 통째로 교체된다. 빈 값으로 보내면 안내 정보가 지워진다
 */
public record CampaignUpdateRequest(
        LocalDateTime openAt,
        Integer totalStock,
        CampaignDetailCommand detail
) {

    public boolean isEmpty() {
        return openAt == null && totalStock == null && detail == null;
    }
}
