package kr.givemeticket.api.campaign.application.dto.response;

import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import kr.givemeticket.api.campaign.domain.ViewerRole;

/**
 * 행사 상세 화면. 프론트가 필드 null 여부로 화면을 추측하지 않도록 {@code viewerRole}을 명시한다.
 *
 * @param remainingStock 조회 시점의 잔여 재고. 읽지 못하면 null
 * @param myApplication  내 신청 내역. 없으면 null
 * @param confirmedCount 확정된 신청 수. OWNER에게만 채워진다
 */
public record CampaignDetailResponse(
        CampaignResponse campaign,
        CampaignOwnerInfo owner,
        Long remainingStock,
        ViewerRole viewerRole,
        MyApplication myApplication,
        Long confirmedCount
) {

    public record MyApplication(
            Long id,
            ApplicationStatus status,
            FailureReason failureReason
    ) {

        public static MyApplication from(Application application) {
            return new MyApplication(
                    application.getId(),
                    application.getStatus(),
                    application.getFailureReason());
        }
    }

    public static CampaignDetailResponse of(
            CampaignSnapshot campaign,
            CampaignOwnerInfo owner,
            Long remainingStock,
            ViewerRole viewerRole,
            Application myApplication,
            Long confirmedCount
    ) {
        return new CampaignDetailResponse(
                CampaignResponse.of(campaign),
                owner,
                remainingStock,
                viewerRole,
                (myApplication == null) ? null : MyApplication.from(myApplication),
                confirmedCount
        );
    }
}
