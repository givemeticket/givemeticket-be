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

    /**
     * 링크는 유효한데 행사가 삭제된 경우. 404와 구분해야 "삭제된 행사입니다" 안내를 띄울 수 있다.
     */
    public static CampaignApplicationException campaignDeleted() {
        return new CampaignApplicationException(HttpStatus.GONE, "CAMPAIGN_DELETED",
                "삭제된 캠페인입니다.");
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

    public static CampaignApplicationException notOwner() {
        return new CampaignApplicationException(HttpStatus.FORBIDDEN, "CAMPAIGN_FORBIDDEN",
                "본인이 만든 캠페인만 관리할 수 있습니다.");
    }

    public static CampaignApplicationException nothingToUpdate() {
        return new CampaignApplicationException(HttpStatus.BAD_REQUEST, "NOTHING_TO_UPDATE",
                "openAt, totalStock, detail 중 하나는 지정해야 합니다.");
    }

    /**
     * 이미 열린 행사에만 걸린다. 오픈 전 행사는 미래이기만 하면 어디로든 옮길 수 있다.
     */
    public static CampaignApplicationException openAtNotDelayable() {
        return new CampaignApplicationException(HttpStatus.CONFLICT, "OPEN_AT_NOT_DELAYABLE",
                "이미 오픈된 행사의 오픈 시각은 기존보다 늦은 시각으로만 변경할 수 있습니다.");
    }

    /**
     * 이미 열린 행사에만 걸린다. 오픈 전 행사는 정원을 줄일 수도 있다.
     */
    public static CampaignApplicationException totalStockNotIncreasable() {
        return new CampaignApplicationException(HttpStatus.CONFLICT, "TOTAL_STOCK_NOT_INCREASABLE",
                "이미 오픈된 행사의 정원은 늘리는 것만 가능합니다.");
    }

    public static CampaignApplicationException shortCodeGenerationFailed() {
        return new CampaignApplicationException(HttpStatus.INTERNAL_SERVER_ERROR, "SHORT_CODE_GENERATION_FAILED",
                "공유 링크 생성에 실패했습니다.");
    }

    public static CampaignApplicationException invalidScope() {
        return new CampaignApplicationException(HttpStatus.BAD_REQUEST, "INVALID_SCOPE",
                "scope는 owned 또는 participated여야 합니다.");
    }
}
