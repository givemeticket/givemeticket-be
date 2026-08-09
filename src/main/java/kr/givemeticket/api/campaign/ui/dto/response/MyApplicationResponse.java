package kr.givemeticket.api.campaign.ui.dto.response;

import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;

public record MyApplicationResponse(
        Long id,
        ApplicationStatus status,
        FailureReason failureReason
) {

    public static MyApplicationResponse from(CampaignDetailResponse.MyApplication myApplication) {
        if (myApplication == null) {
            return null;
        }
        return new MyApplicationResponse(
                myApplication.id(),
                myApplication.status(),
                myApplication.failureReason()
        );
    }
}
