package kr.givemeticket.api.campaign.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.givemeticket.api.campaign.ui.dto.request.PatchCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.response.CloseCampaignResponse;
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
            description = """
                    공유 링크의 shortCode로 조회합니다. 인증은 선택이며, 토큰 유무와 소유·신청 여부에 따라
                    viewerRole이 GUEST / VIEWER / PARTICIPANT / OWNER로 내려갑니다.
                    토큰을 보냈는데 유효하지 않으면 401입니다.

                    - 개설자 정보는 owner(id/nickname/profileImageUrl)에 담깁니다
                    - 잔여 재고(remainingStock)와 매진 여부(soldOut)가 함께 내려갑니다. 첫 화면을 한 번에
                      그리기 위한 조회 시점 스냅샷이며, 이후 갱신은 GET /campaigns/{campaignId}/stock 으로
                      폴링하세요. 재고를 읽지 못한 경우에도 조회는 성공하고 두 값이 null 로 옵니다
                    - 행사 안내 정보는 detail에 담기며 등록된 게 없으면 null입니다
                    - 삭제된 캠페인은 410을 반환합니다
                    """)
    ResponseEntity<GetCampaignResponse> readCampaign(
            @Parameter(hidden = true) @LoginUserId(required = false) Long userId,
            @Parameter(description = "공유 링크 코드", example = "3AbCdEfGh1")
            @PathVariable("shortCode") String shortCode
    );

    @Operation(summary = "잔여 재고 조회",
            description = "잔여 재고와 매진 여부만 내려주는 폴링용 API입니다. 상세·목록 응답에도 같은 값이 "
                    + "들어 있지만 그건 첫 화면용 스냅샷이고, 이후 갱신은 이 API로 받으세요. "
                    + "DB를 거치지 않고 Redis만 읽습니다. "
                    + "인증은 필요 없습니다. 없거나 삭제된 캠페인은 404입니다.")
    ResponseEntity<GetCampaignStockResponse> readCampaignStock(
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );

    @Operation(summary = "캠페인 목록 조회",
            description = """
                    owned는 내가 만든 행사, participated는 내가 참여중인 행사(나의 티켓)입니다.

                    - 목록에는 카드에 필요한 eventAt/location/imageUrl만 펼쳐지고 본문은 상세 조회에서만 내려갑니다
                    - 개설자 정보는 owner(id/nickname/profileImageUrl)에 담깁니다
                    - 카드마다 재고를 따로 부르지 않도록 remainingStock/soldOut 이 함께 내려갑니다.
                      삭제된 행사이거나 재고를 읽지 못하면 두 값이 null 입니다
                    - 삭제한 행사도 status=DELETED 로 남습니다. 목록에서 지우지 않고 "삭제됨"으로
                      보여주면 됩니다. participated 도 마찬가지로, 주최자가 지운 행사는
                      myApplicationStatus=CANCELLED 인 채로 남습니다. 다만 내가 직접 취소한 행사는
                      빠집니다 — 사라진 이유를 이미 알고 있으니까요
                    """)
    ResponseEntity<GetCampaignsResponse> readCampaigns(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "조회 범위", example = "owned",
                    schema = @Schema(allowableValues = {"owned", "participated"}))
            @RequestParam("scope") String scope
    );

    @Operation(summary = "캠페인 수정",
            description = """
                    개설자만 호출할 수 있습니다. 제한은 이미 오픈된 행사에만 걸립니다.

                    아직 오픈 전(status=SCHEDULED)이면
                    - openAt 은 미래 시각이기만 하면 앞당기든 미루든 자유입니다
                    - totalStock 도 자유롭습니다. 신청자가 없으므로 줄여도 됩니다

                    이미 오픈된 뒤(status=OPEN)라면
                    - openAt 은 지금 설정된 시각보다 뒤로만 옮길 수 있습니다. 미루면 접수가 멈추고
                      status 가 SCHEDULED 로 돌아가며, 새 오픈 시각이 되면 다시 열립니다.
                      이미 들어온 신청은 그대로 유지됩니다. 앞당기려 하면 409 `OPEN_AT_NOT_DELAYABLE`
                    - totalStock 은 늘리는 것만 됩니다. 줄이려 하면 409 `TOTAL_STOCK_NOT_INCREASABLE`.
                      매진 상태에서 증원하면 자동으로 다시 신청 가능해집니다

                    지금과 같은 값을 보내는 것은 오류가 아니라 무시입니다. 폼 전체를 그대로 보내도
                    정원만 바꾸거나 오픈 시각만 바꾸는 요청이 그대로 통과합니다.

                    detail은 지정하면 통째로 교체되며, 빈 값으로 보내면 안내 정보가 지워집니다.
                    """)
    ResponseEntity<PatchCampaignResponse> updateCampaign(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId,
            @Valid @RequestBody PatchCampaignRequest request
    );

    @Operation(summary = "캠페인 종료",
            description = """
                    개설자만 호출할 수 있습니다. 더 이상 신청을 받지 않고 status 가 CLOSED 가 됩니다.

                    - 이미 확정된 신청은 그대로 유효합니다. 삭제와 달리 취소도 환불도 하지 않습니다
                    - 결제 대기(PENDING)인 신청은 홀드 시간 안에 결제를 끝낼 수 있습니다.
                      종료 전에 이미 자리를 잡은 건이라 중간에 끊지 않습니다
                    - 잔여 재고는 계속 조회됩니다. 몇 자리가 나갔는지는 종료 후에도 보여야 하기 때문입니다
                    - 오픈 전(SCHEDULED)인 행사도 종료할 수 있습니다. 그 경우 예정된 시각이 와도 열리지 않습니다
                    - 되돌리는 API 는 없습니다. 종료된 행사는 오픈 시각도 바꿀 수 없고
                      (409 `CAMPAIGN_CLOSED`), 다시 열려면 새로 만들어야 합니다
                    - 두 번 호출해도 같은 결과입니다
                    """)
    ResponseEntity<CloseCampaignResponse> closeCampaign(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
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
