package kr.givemeticket.api.wish.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import kr.givemeticket.api.wish.ui.dto.response.WishStatusResponsePart;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "찜 API", description = "행사 찜하기·해제와 찜 수 조회 API 명세입니다.")
public interface WishApiSpec {

    @Operation(summary = "찜하기",
            description = """
                    행사를 찜합니다. 찜한 행사는 신청이 열리는 순간 알림(WISHED_CAMPAIGN_OPENED)이 갑니다.

                    - 이미 찜한 행사에 다시 불러도 200 입니다. 찜은 하나만 남습니다
                    - 응답의 wishCount 는 반영된 뒤의 찜 수입니다. 하트 옆 숫자를 이 값으로 바꾸면 됩니다
                    - 종료된 행사도 찜할 수 있습니다
                    - 없는 행사는 404 CAMPAIGN_NOT_FOUND, 삭제된 행사는 410 CAMPAIGN_DELETED
                    """)
    ResponseEntity<WishStatusResponsePart> wish(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );

    @Operation(summary = "찜 해제",
            description = """
                    찜을 해제합니다.

                    - 찜하지 않은 행사에 불러도 200 입니다
                    - 삭제된 행사도 해제할 수 있습니다. 찜 목록(GET /campaigns?scope=wished)에
                      삭제된 행사가 남아 있으니 거기서 하트를 끌 수 있어야 합니다
                    """)
    ResponseEntity<WishStatusResponsePart> unwish(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );

    @Operation(summary = "찜 수 조회",
            description = """
                    행사의 찜 수를 내려줍니다. 로그인하지 않아도 됩니다.

                    - 토큰을 보내면 wished 에 내가 찜했는지가 담깁니다. 하트를 채울지 이 값으로 정하면 됩니다
                    - 토큰 없이 부르면 wished 는 null 입니다
                    - 없는 행사는 404 CAMPAIGN_NOT_FOUND, 삭제된 행사는 410 CAMPAIGN_DELETED
                    """)
    ResponseEntity<WishStatusResponsePart> readWishStatus(
            @Parameter(hidden = true) @LoginUserId(required = false) Long userId,
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );
}
