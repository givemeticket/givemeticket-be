package kr.givemeticket.api.campaign.application.dto;

import java.util.Locale;
import kr.givemeticket.api.campaign.application.CampaignApplicationException;

public enum CampaignScope {

    /** 내가 만든 행사 */
    OWNED,

    /** 내가 참여중인 행사 (나의 티켓) */
    PARTICIPATED;

    /**
     * 쿼리 파라미터는 소문자로 온다. Spring 기본 enum 변환은 대소문자를 구분해서 직접 처리한다.
     */
    public static CampaignScope from(String value) {
        if (value == null || value.isBlank()) {
            throw CampaignApplicationException.invalidScope();
        }
        try {
            return CampaignScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw CampaignApplicationException.invalidScope();
        }
    }
}
