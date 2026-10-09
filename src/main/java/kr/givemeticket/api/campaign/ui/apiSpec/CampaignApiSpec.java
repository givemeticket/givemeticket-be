package kr.givemeticket.api.campaign.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kr.givemeticket.api.campaign.ui.dto.request.PatchCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.response.CloseCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.CreateCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignPageResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignStockResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignsResponse;
import kr.givemeticket.api.campaign.ui.dto.response.PatchCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.SearchCampaignsResponse;
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
                    owned는 내가 만든 행사, participated는 내가 참여중인 행사(나의 티켓),
                    wished는 내가 찜한 행사입니다.

                    - 목록에는 카드에 필요한 eventAt/location/imageUrl만 펼쳐지고 본문은 상세 조회에서만 내려갑니다
                    - 개설자 정보는 owner(id/nickname/profileImageUrl)에 담깁니다
                    - 카드마다 재고를 따로 부르지 않도록 remainingStock/soldOut 이 함께 내려갑니다.
                      삭제된 행사이거나 재고를 읽지 못하면 두 값이 null 입니다
                    - 삭제한 행사도 status=DELETED 로 남습니다. 목록에서 지우지 않고 "삭제됨"으로
                      보여주면 됩니다. participated 도 마찬가지로, 주최자가 지운 행사는
                      myApplicationStatus=CANCELLED 인 채로 남습니다 — 다시 신청할 수단이
                      없으니 여기서라도 무슨 일이 있었는지 보여야 합니다
                    - 반대로 신청이 살아 있지 않고 행사는 멀쩡한 경우는 빠집니다. 내가 직접
                      취소한 행사와, 주최자가 내 신청만 취소한 행사(CANCELLED_BY_OWNER)입니다.
                      둘 다 링크로 들어가 다시 신청할 수 있어서, 목록에 취소 카드로 남기면
                      끝난 행사처럼 보입니다. 왜 취소됐는지는 GET /applications/{applicationId} 의
                      failureReason 으로 확인하세요
                    - participated 는 최근 신청이 위로 오도록 myAppliedAt 내림차순으로 내려갑니다.
                      취소했다가 다시 신청하면 myAppliedAt 이 재신청 시각으로 갱신되어 맨 위로
                      올라옵니다. owned 에서는 myApplicationStatus/myAppliedAt 이 null 입니다
                    - wished 는 최근에 찜한 행사가 위로 옵니다. 주최자가 지운 행사도 status=DELETED 로
                      남고, DELETE /campaigns/{campaignId}/wish 로 해제하면 빠집니다.
                      myApplicationStatus/myAppliedAt 은 null 입니다
                    """)
    ResponseEntity<GetCampaignsResponse> readCampaigns(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "조회 범위", example = "owned",
                    schema = @Schema(allowableValues = {"owned", "participated", "wished"}))
            @RequestParam("scope") String scope
    );

    @Operation(summary = "캠페인 검색",
            description = """
                    검색어와 필터로 행사를 찾습니다. 로그인하지 않아도 됩니다. 삭제된 행사는 나오지 않습니다.

                    검색어
                    - keyword 는 선택입니다. 비우면 제목 조건 없이 필터만으로 찾습니다.
                      아무 조건도 주지 않으면 삭제되지 않은 행사 전체가 나옵니다
                    - 앞뒤 공백은 떼고, 100자를 넘으면 400 INVALID_KEYWORD
                    - 대소문자를 구분하지 않습니다. %, _ 도 와일드카드가 아니라 글자로 찾습니다

                    상태 칩 (status, soldOut)
                    - status 는 OPEN·SCHEDULED·CLOSED 를 여러 개 고를 수 있습니다(status=OPEN&status=SCHEDULED).
                      그 밖의 값은 400 INVALID_STATUS
                    - soldOut=true 는 매진 칩입니다
                    - 고른 칩끼리는 OR 입니다. 하나도 고르지 않으면 상태 조건이 없습니다
                    - 진행중(OPEN)과 매진은 겹치지 않습니다. status=OPEN 은 매진된 행사를 빼고,
                      soldOut=true 는 진행 중이면서 매진된 행사만 냅니다. 종료(CLOSED)된 행사는 매진이었어도
                      종료로만 셉니다
                    - 매진은 확정된 신청 수로 판단합니다. 방금 잡힌 자리는 1초 안팎 늦게 반영될 수 있고,
                      그 사이에는 카드의 soldOut(실시간 재고)과 칩 결과가 잠깐 어긋날 수 있습니다

                    오픈 날짜 (openFrom, openTo)
                    - YYYY-MM-DD, 한국 시간 기준이고 양 끝 날짜를 포함합니다. 둘 다 선택입니다
                    - 형식이 틀리거나 openFrom 이 openTo 보다 늦으면 400 INVALID_OPEN_DATE

                    정렬과 페이징
                    - sort 는 openAt,asc / openAt,desc 입니다. 비우면 최근에 만든 행사부터입니다.
                      그 밖의 값은 400 INVALID_SORT
                    - 첫 페이지는 cursor 없이 부르고, 다음 페이지는 응답의 nextCursor 를 그대로 넘깁니다.
                      nextCursor 는 문자열이고 정렬마다 모양이 다르니 해석하지 마세요. null 이면 마지막 페이지입니다
                    - 정렬을 바꾸면 첫 페이지부터 다시 부르세요. 다른 정렬의 커서는 400 INVALID_CURSOR 일 수 있습니다
                    - size 는 기본 20, 최대 50 입니다. 벗어나면 400 INVALID_PAGE_SIZE
                    - totalCount 는 필터를 적용한 전체 건수입니다. 커서와 상관없이 같은 값입니다

                    카드 모양은 GET /campaigns 와 같습니다. myApplicationStatus/myAppliedAt 은 null 입니다
                    """)
    ResponseEntity<SearchCampaignsResponse> searchCampaigns(
            @Parameter(description = "검색어. 선택", example = "콘서트")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "상태 칩. 여러 개 가능",
                    array = @ArraySchema(schema = @Schema(allowableValues = {"OPEN", "SCHEDULED", "CLOSED"})))
            @RequestParam(value = "status", required = false) List<String> statuses,
            @Parameter(description = "매진 칩", example = "true")
            @RequestParam(value = "soldOut", required = false) Boolean soldOut,
            @Parameter(description = "오픈 날짜 시작(포함). YYYY-MM-DD, 한국 시간", example = "2026-10-01")
            @RequestParam(value = "openFrom", required = false) String openFrom,
            @Parameter(description = "오픈 날짜 끝(포함). YYYY-MM-DD, 한국 시간", example = "2026-10-31")
            @RequestParam(value = "openTo", required = false) String openTo,
            @Parameter(description = "정렬. 비우면 최신순",
                    schema = @Schema(allowableValues = {"openAt,asc", "openAt,desc"}))
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지면 비웁니다")
            @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(description = "페이지 크기. 기본 20, 최대 50", example = "20")
            @RequestParam(value = "size", required = false) Integer size
    );

    @Operation(summary = "작성자로 캠페인 검색",
            description = """
                    한 사람이 연 행사를 최신순으로 내려줍니다. 로그인하지 않아도 됩니다.

                    - 남이 보는 목록이라 삭제된 행사는 빠집니다. 내가 연 행사를 삭제된 것까지 보려면
                      GET /campaigns?scope=owned 를 쓰세요
                    - 없는 사용자이거나 연 행사가 없으면 빈 목록입니다
                    - 페이징 규칙은 제목 검색과 같습니다
                    """)
    ResponseEntity<GetCampaignPageResponse> readCampaignsOwnedBy(
            @Parameter(description = "작성자(개설자) 사용자 ID", example = "1")
            @PathVariable("userId") Long ownerId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지면 비웁니다", example = "120")
            @RequestParam(value = "cursor", required = false) Long cursor,
            @Parameter(description = "페이지 크기. 기본 20, 최대 50", example = "20")
            @RequestParam(value = "size", required = false) Integer size
    );

    @Operation(summary = "캠페인 수정",
            description = """
                    개설자만 호출할 수 있습니다. 제한은 이미 오픈된 행사에만 걸립니다.

                    title 은 오픈 여부와 상관없이 언제든 바꿀 수 있습니다. 보내지 않으면 그대로 두고,
                    보낸다면 공백일 수 없습니다(400).

                    아직 오픈 전(status=SCHEDULED)이면
                    - openAt 은 미래 시각이기만 하면 앞당기든 미루든 자유입니다
                    - totalStock 은 줄일 수도 있지만, 이미 신청한 인원이 하한입니다. 그보다 적게
                      줄이려 하면 409 `TOTAL_STOCK_BELOW_APPLICANTS`. 한 번도 열린 적 없는 행사는
                      신청자가 없으므로 사실상 제한이 없고, 오픈을 미뤄 SCHEDULED 로 돌아온
                      행사에서만 걸립니다

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

                    - 이미 확정된 신청은 그대로 유효합니다. 삭제와 달리 취소하지 않습니다
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
                    - 삭제된 캠페인을 다시 삭제하면 410 CAMPAIGN_DELETED
                    """)
    ResponseEntity<Void> deleteCampaign(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );
}
