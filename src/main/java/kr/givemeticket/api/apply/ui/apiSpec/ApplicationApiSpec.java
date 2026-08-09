package kr.givemeticket.api.apply.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.apply.ui.dto.response.CancelApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.ConfirmApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetApplicationResponse;
import kr.givemeticket.api.global.web.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "신청 API", description = "선착순 신청 관련 API 명세입니다.")
public interface ApplicationApiSpec {

    @Operation(summary = "선착순 신청",
            description = """
                    재고를 잡습니다. 결제는 하지 않습니다.

                    - 결제가 없는 캠페인: 바로 CONFIRMED
                    - 결제가 있는 캠페인: PENDING. 응답의 expiresAt까지 확정 API를 호출해야 하며,
                      넘기면 자리가 자동으로 회수됩니다
                    - 매진 409 SOLD_OUT / 중복 409 ALREADY_APPLIED
                    """)
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<ApplyResponse> apply(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );

    @Operation(summary = "신청 확정(결제)",
            description = """
                    PENDING 신청의 결제를 요청하고 결과에 따라 상태를 확정합니다.

                    - 승인: CONFIRMED (200)
                    - 결제 결과를 받지 못함: UNKNOWN (202). 재고를 잡아둔 채 정산을 기다리므로
                      신청 조회 API를 폴링해 최종 상태를 확인합니다
                    - 거절 409 PAYMENT_DECLINED / 게이트웨이 오류 502 PAYMENT_GATEWAY_ERROR
                      — 둘 다 재고가 반납됩니다
                    - 홀드 만료 409 APPLICATION_EXPIRED / 그 밖의 상태 409 APPLICATION_NOT_PENDING
                    """)
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<ConfirmApplicationResponse> confirmApplication(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );

    @Operation(summary = "신청 취소",
            description = """
                    확정된 신청만 취소할 수 있습니다. 취소되면 재고가 즉시 반납됩니다.

                    - 결제가 없던 신청: 외부 호출 없이 즉시 취소. `refundStatus`는 NOT_REQUIRED
                    - 결제가 있던 신청: 취소·재고 반납을 먼저 끝내고 환불을 요청합니다.
                      환불까지 성공하면 COMPLETED, 환불 요청이 실패하면 PENDING_RETRY이며
                      이 경우에도 신청 취소는 되돌리지 않습니다
                    - 결제 결과 확인 중(UNKNOWN)인 신청은 409 APPLICATION_SETTLEMENT_PENDING
                    - 그 밖의 상태는 409 APPLICATION_NOT_CANCELABLE
                    """)
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<CancelApplicationResponse> cancelApplication(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );

    @Operation(summary = "신청 내역 조회",
            description = "본인의 신청 내역만 조회할 수 있습니다. UNKNOWN 상태를 폴링할 때 씁니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<GetApplicationResponse> readApplication(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );
}
