package kr.givemeticket.api.apply.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.apply.ui.dto.response.CancelApplicantResponse;
import kr.givemeticket.api.apply.ui.dto.response.CancelApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetCampaignApplicantsResponse;
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
                      CAMPAIGN_DELETED(주최자가 행사 삭제) / USER_WITHDRAWN(본인 탈퇴) /
                      CANCELLED_BY_OWNER(주최자가 내 신청만 취소)
                    """)
    ResponseEntity<GetApplicationResponse> readApplication(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );

    @Operation(summary = "신청자 목록 조회 (주최자)",
            description = """
                    행사 개설자만 호출할 수 있습니다. 자리를 잡고 있는(CONFIRMED) 신청자만
                    신청한 순서대로 내려갑니다.

                    - appliedAt 은 자리를 잡은 시각입니다. 이 순서가 곧 선착순 순서입니다.
                      취소했다가 다시 신청했다면 다시 신청한 시각으로 갱신됩니다
                    - 취소된 신청은 목록에 없습니다
                    - 신청자 수는 정원을 넘지 않으므로 페이징은 없습니다
                    - 방금 들어온 신청은 저장이 끝나기 전이라 잠깐 빠져 있을 수 있습니다.
                      정확한 잔여 자리 수는 GET /campaigns/{campaignId}/stock 을 쓰세요
                    - 남의 행사면 403 CAMPAIGN_FORBIDDEN, 삭제된 행사면 410 CAMPAIGN_DELETED
                    """)
    ResponseEntity<GetCampaignApplicantsResponse> readCampaignApplicants(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );

    @Operation(summary = "신청자 취소 (주최자)",
            description = """
                    행사 개설자가 신청자 한 명을 내보냅니다. 확정된 신청만 대상입니다.

                    - 비운 자리는 즉시 재고로 돌아가 다른 사람이 신청할 수 있습니다
                    - 내보낸 사람이 다시 신청하는 것은 막지 않습니다. 차단이 아니라 취소입니다
                    - 신청자에게는 status=CANCELLED, failureReason=CANCELLED_BY_OWNER 로 보입니다.
                      본인이 누른 취소와 달리 "나의 티켓" 목록에도 계속 남습니다
                    - 종료(CLOSED)된 행사에서도 호출할 수 있습니다
                    - 이 행사의 신청이 아니면 404 APPLICATION_NOT_FOUND. 방금 들어온 신청은
                      저장이 끝나기 전이라 잠깐 이 응답이 나올 수 있습니다. 목록을 새로 받아보세요
                    - 확정 상태가 아니면 409 APPLICATION_NOT_CANCELABLE
                    - 남의 행사면 403 CAMPAIGN_FORBIDDEN, 삭제된 행사면 410 CAMPAIGN_DELETED
                    """)
    ResponseEntity<CancelApplicantResponse> cancelApplicantByOwner(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId,
            @Parameter(description = "신청 ID", example = "1")
            @PathVariable("applicationId") Long applicationId
    );
}
