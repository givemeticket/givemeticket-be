package kr.givemeticket.api.campaign.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.givemeticket.api.campaign.ui.dto.request.PatchCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.response.CreateCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignStockResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignsResponse;
import kr.givemeticket.api.campaign.ui.dto.response.PatchCampaignResponse;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "캠페인 API", description = "선착순 티켓 캠페인 관련 API 명세입니다.")
public interface CampaignApiSpec {

    @Operation(summary = "캠페인 등록",
            description = "캠페인을 등록하고 잔여 재고를 초기화합니다. 응답의 shortCode가 공유 링크가 됩니다. "
                    + "openAt은 UTC 기준 미래 시각이어야 합니다. "
                    + "detail(행사 안내 정보)은 선택이며, 그 안의 필드도 전부 선택입니다.")
    ResponseEntity<CreateCampaignResponse> createCampaign(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Valid @RequestBody PostCampaignRequest request
    );

    @Operation(summary = "캠페인 상세 조회",
            description = "공유 링크의 shortCode로 조회합니다. 인증은 선택이며, 토큰 유무와 소유·신청 여부에 따라 "
                    + "viewerRole이 GUEST / VIEWER / PARTICIPANT / OWNER로 내려갑니다. "
                    + "토큰을 보냈는데 유효하지 않으면 401입니다. "
                    + "행사 안내 정보는 detail에 담기며 등록된 게 없으면 null입니다. "
                    + "삭제된 캠페인은 410을 반환합니다. "
                    + "잔여 재고와 매진 여부는 여기 없고 GET /campaigns/{campaignId}/stock 에서 받습니다.")
    ResponseEntity<GetCampaignResponse> readCampaign(
            @Parameter(hidden = true) @LoginUserId(required = false) Long userId,
            @Parameter(description = "공유 링크 코드", example = "3AbCdEfGh1")
            @PathVariable("shortCode") String shortCode
    );

    @Operation(summary = "잔여 재고 조회",
            description = "잔여 재고와 매진 여부만 내려줍니다. 상세·목록 조회보다 훨씬 자주 폴링되는 값이라 "
                    + "별도 API로 분리했고, DB를 거치지 않고 Redis만 읽습니다. "
                    + "인증은 필요 없습니다. 없거나 삭제된 캠페인은 404입니다.")
    ResponseEntity<GetCampaignStockResponse> readCampaignStock(
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );

    @Operation(summary = "캠페인 목록 조회",
            description = "owned는 내가 만든 행사, participated는 내가 참여중인 행사(나의 티켓)입니다. "
                    + "목록에는 카드에 필요한 eventAt/location/imageUrl만 펼쳐지고 본문은 상세 조회에서만 내려갑니다. "
                    + "잔여 재고와 매진 여부는 여기 없고 GET /campaigns/{campaignId}/stock 에서 받습니다.")
    ResponseEntity<GetCampaignsResponse> readCampaigns(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "조회 범위", example = "owned",
                    schema = @Schema(allowableValues = {"owned", "participated"}))
            @RequestParam("scope") String scope
    );

    @Operation(summary = "캠페인 수정",
            description = "개설자만 호출할 수 있습니다. 오픈 시각은 오픈 전에 더 늦은 시각으로만, "
                    + "정원은 늘리는 방향으로만 바꿀 수 있습니다. 매진 상태에서 증원하면 자동으로 다시 신청 가능해집니다. "
                    + "detail은 지정하면 통째로 교체되며, 빈 값으로 보내면 안내 정보가 지워집니다.")
    ResponseEntity<PatchCampaignResponse> updateCampaign(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId,
            @Valid @RequestBody PatchCampaignRequest request
    );

    @Operation(summary = "캠페인 삭제",
            description = """
                    개설자만 호출할 수 있습니다. 신청자가 있어도 삭제되며, 되돌릴 수 없습니다.

                    - 남아 있던 신청은 전부 CANCELLED 가 되고 failureReason 에 CAMPAIGN_DELETED 가 찍힙니다
                    - 결제가 끝난 신청은 환불이 요청됩니다. 환불에 실패해도 삭제와 취소는 되돌리지 않고
                      로그로 남겨 뒤에서 다시 시도합니다
                    - 삭제된 캠페인을 다시 삭제하면 410 CAMPAIGN_DELETED
                    """)
    ResponseEntity<Void> deleteCampaign(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );
}
