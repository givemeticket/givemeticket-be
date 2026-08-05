package kr.givemeticket.api.campaign.application;

import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CampaignApplicationException extends BusinessException {

    private CampaignApplicationException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static CampaignApplicationException campaignNotFound() {
        return new CampaignApplicationException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND",
                "캠페인을 찾을 수 없습니다.");
    }

    public static CampaignApplicationException notOpen() {
        return new CampaignApplicationException(HttpStatus.CONFLICT, "CAMPAIGN_NOT_OPEN",
                "아직 오픈되지 않았거나 종료된 캠페인입니다.");
    }

    public static CampaignApplicationException soldOut() {
        return new CampaignApplicationException(HttpStatus.CONFLICT, "SOLD_OUT",
                "매진되었습니다.");
    }

    public static CampaignApplicationException stockNotInitialized() {
        return new CampaignApplicationException(HttpStatus.INTERNAL_SERVER_ERROR, "STOCK_NOT_INITIALIZED",
                "재고가 초기화되지 않았습니다.");
    }
}
