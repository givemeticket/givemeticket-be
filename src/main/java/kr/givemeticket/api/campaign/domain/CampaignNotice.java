package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.givemeticket.api.campaign.domain.CampaignChange.Field;
import kr.givemeticket.api.global.time.Utc;

/**
 * 신청자에게 알릴 가치가 있는 항목만 뽑은 스냅샷. 수정 전후를 비교해 무엇이 바뀌었는지 낸다.
 *
 * <p>수정 API 는 폼 전체를 그대로 받는다. 요청에 값이 실렸다는 것만으로는 바뀌었는지 알 수 없어서
 * 실제 값을 비교한다.
 */
public record CampaignNotice(
        String title,
        LocalDateTime eventAt,
        LocalDateTime eventEndAt,
        String location,
        String address
) {

    public static CampaignNotice of(Campaign campaign) {
        CampaignDetail detail = campaign.getDetail();
        if (detail == null) {
            return new CampaignNotice(campaign.getTitle(), null, null, null, null);
        }
        return new CampaignNotice(
                campaign.getTitle(),
                detail.getEventAt(),
                detail.getEventEndAt(),
                detail.getLocation(),
                detail.getAddress());
    }

    public List<CampaignChange> diff(CampaignNotice after) {
        List<CampaignChange> changes = new ArrayList<>();
        addIfChanged(changes, Field.TITLE, title, after.title);
        addIfChanged(changes, Field.EVENT_AT, format(eventAt), format(after.eventAt));
        addIfChanged(changes, Field.EVENT_END_AT, format(eventEndAt), format(after.eventEndAt));
        addIfChanged(changes, Field.LOCATION, location, after.location);
        addIfChanged(changes, Field.ADDRESS, address, after.address);
        return changes;
    }

    private static void addIfChanged(
            List<CampaignChange> changes, Field field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new CampaignChange(field, before, after));
        }
    }

    /** 응답과 같은 형식(UTC, Z)으로 둔다. 프론트가 알림 문구에서도 같은 파서를 쓸 수 있다. */
    private static String format(LocalDateTime dateTime) {
        return (dateTime == null) ? null : Utc.toInstant(dateTime).toString();
    }
}
