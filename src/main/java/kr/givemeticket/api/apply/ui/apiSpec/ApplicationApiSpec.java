package kr.givemeticket.api.apply.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.apply.ui.dto.response.CancelApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetApplicationResponse;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "신청 API", description = "선착순 신청 관련 API 명세입니다.")
public interface ApplicationApiSpec {

    @Operation(summary = "선착순 신청",
            description = """
                    재고를 잡고 그 자리에서 확정합니다. 결제 단계는 없습니다.

                    - 성공하면 바로 CONFIRMED 입니다. 이어서 부를 확정 API 는 없습니다
                    - 매진 409 SOLD_OUT / 중복 409 ALREADY_APPLIED
                    - 오픈 전이거나 종료된 행사는 409 CAMPAIGN_NOT_OPEN
                    """)
    ResponseEntity<ApplyResponse> apply(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );

    @Operation(summary = "신청 취소",
            description = """
                    확정된 신청만 취소할 수 있습니다. 취소되면 재고가 즉시 반납됩니다.

                    - 그 밖의 상태는 409 APPLICATION_NOT_CANCELABLE
                    - 취소한 행사는 자리가 남아 있으면 다시 신청할 수 있습니다
                    """)
    ResponseEntity<CancelApplicationResponse> cancelApplication(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );

    @Operation(summary = "신청 내역 조회",
            description = """
                    본인의 신청 내역만 조회할 수 있습니다.

                    - failureReason 은 내가 누른 취소가 아닐 때만 채워집니다.
                      CAMPAIGN_DELETED(주최자가 행사 삭제) / USER_WITHDRAWN(본인 탈퇴)
                    """)
    ResponseEntity<GetApplicationResponse> readApplication(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );
}
