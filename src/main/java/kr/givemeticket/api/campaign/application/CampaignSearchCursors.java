package kr.givemeticket.api.campaign.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import kr.givemeticket.api.campaign.domain.CampaignCursor;
import kr.givemeticket.api.campaign.domain.CampaignSort;

/**
 * 커서를 문자열로 주고받는다. 프론트는 값의 모양을 알 필요 없이 받은 그대로 돌려주면 된다.
 *
 * <p>최신순 커서는 id 숫자 그대로다. 이 API 가 숫자 커서를 쓰던 때 받아 둔 값도 그대로 통한다.
 * 오픈 시각 정렬은 오픈 시각과 id 를 함께 담아야 해서 base64url 로 감싼다.
 */
final class CampaignSearchCursors {

    private static final String SEPARATOR = "|";

    private CampaignSearchCursors() {
    }

    static String encode(CampaignCursor cursor, CampaignSort sort) {
        if (sort == CampaignSort.LATEST) {
            return String.valueOf(cursor.id());
        }
        String raw = cursor.openAt() + SEPARATOR + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return 커서가 없으면 null
     * @throws CampaignApplicationException 정렬과 맞지 않거나 깨진 커서
     */
    static CampaignCursor decode(String value, CampaignSort sort) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (sort == CampaignSort.LATEST) {
                return CampaignCursor.ofId(Long.parseLong(value.trim()));
            }
            String raw = new String(Base64.getUrlDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            int at = raw.lastIndexOf(SEPARATOR);
            if (at < 0) {
                throw CampaignApplicationException.invalidCursor();
            }
            return new CampaignCursor(
                    LocalDateTime.parse(raw.substring(0, at)), Long.parseLong(raw.substring(at + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw CampaignApplicationException.invalidCursor();
        }
    }
}
