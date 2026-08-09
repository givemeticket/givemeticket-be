package kr.givemeticket.api.campaign.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.givemeticket.api.campaign.ui.dto.request.PatchCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.response.CreateCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignsResponse;
import kr.givemeticket.api.campaign.ui.dto.response.PatchCampaignResponse;
import kr.givemeticket.api.global.web.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "캠페인 API", description = "선착순 티켓 캠페인 관련 API 명세입니다.")
public interface CampaignApiSpec {

    @Operation(summary = "캠페인 등록",
            description = "캠페인을 등록하고 잔여 재고를 초기화합니다. 응답의 shortCode가 공유 링크가 됩니다. "
                    + "openAt은 UTC 기준 미래 시각이어야 합니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<CreateCampaignResponse> createCampaign(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Valid @RequestBody PostCampaignRequest request
    );

    @Operation(summary = "캠페인 상세 조회",
            description = "공유 링크의 shortCode로 조회합니다. 인증은 선택이며, 헤더 유무와 소유·신청 여부에 따라 "
                    + "viewerRole이 GUEST / VIEWER / PARTICIPANT / OWNER로 내려갑니다. "
                    + "삭제된 캠페인은 410을 반환합니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자 (없으면 비로그인 조회)", in = ParameterIn.HEADER,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<GetCampaignResponse> readCampaign(
            @Parameter(hidden = true) @CurrentUserId(required = false) Long userId,
            @Parameter(description = "공유 링크 코드", example = "3AbCdEfGh1")
            @PathVariable("shortCode") String shortCode
    );

    @Operation(summary = "캠페인 목록 조회",
            description = "owned는 내가 만든 행사, participated는 내가 참여중인 행사(나의 티켓)입니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<GetCampaignsResponse> readCampaigns(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "조회 범위", example = "owned",
                    schema = @Schema(allowableValues = {"owned", "participated"}))
            @RequestParam("scope") String scope
    );

    @Operation(summary = "캠페인 수정",
            description = "개설자만 호출할 수 있습니다. 오픈 시각은 오픈 전에 더 늦은 시각으로만, "
                    + "정원은 늘리는 방향으로만 바꿀 수 있습니다. 매진 상태에서 증원하면 자동으로 다시 신청 가능해집니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<PatchCampaignResponse> updateCampaign(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId,
            @Valid @RequestBody PatchCampaignRequest request
    );

    @Operation(summary = "캠페인 삭제",
            description = "개설자만 호출할 수 있습니다. 유효한 신청이 하나라도 남아 있으면 409를 반환합니다.")
    @Parameter(name = "X-User-Id", description = "유저 식별자", in = ParameterIn.HEADER, required = true,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    ResponseEntity<Void> deleteCampaign(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );
}
