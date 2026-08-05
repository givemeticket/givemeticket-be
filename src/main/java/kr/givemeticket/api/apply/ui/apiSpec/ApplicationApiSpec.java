package kr.givemeticket.api.apply.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.apply.ui.dto.response.ConfirmApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetApplicationResponse;
import kr.givemeticket.api.global.web.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "신청 API", description = "선착순 신청과 결제 확정 관련 API 명세입니다.")
public interface ApplicationApiSpec {

    @Operation(summary = "선착순 신청", description = "재고를 차감하고 신청을 PENDING으로 기록합니다. 매진이면 409를 반환합니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<ApplyResponse> apply(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );

    @Operation(summary = "신청 내역 조회", description = "본인의 신청 내역만 조회할 수 있습니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<GetApplicationResponse> readApplication(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );

    @Operation(summary = "결제 확정", description = "결제를 요청해 신청을 확정합니다. 거절되면 FAILED로 바뀌고 재고가 반납됩니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<ConfirmApplicationResponse> confirmApplication(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );
}
