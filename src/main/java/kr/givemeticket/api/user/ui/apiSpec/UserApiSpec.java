package kr.givemeticket.api.user.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import kr.givemeticket.api.user.ui.dto.response.GetUserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "유저 API", description = "계정 관련 API 명세입니다.")
public interface UserApiSpec {

    @Operation(summary = "사용자 정보 조회",
            description = """
                    userId 로 사용자의 공개 정보(닉네임·프로필 이미지)를 조회합니다. 인증은 필요 없습니다.
                    행사 응답의 ownerId 처럼 화면에 userId 만 들고 있을 때 씁니다.

                    - profileImageUrl 은 소셜 프로필 이미지입니다. 카카오는 ID 토큰의 picture,
                      네이버는 프로필 API 의 profile_image 를 그대로 저장하며, 둘 다 선택 동의 항목이라
                      사용자가 동의하지 않았으면 null 입니다
                    - 탈퇴한 계정도 404 가 아니라 200 으로 내려가고 withdrawn=true 가 붙습니다.
                      닉네임은 "탈퇴한 사용자", 프로필 이미지는 null 입니다
                    - 없는 userId 는 404 `USER_NOT_FOUND` 입니다
                    """)
    ResponseEntity<GetUserResponse> readUser(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable("userId") Long userId
    );

    @Operation(summary = "회원 탈퇴",
            description = """
                    내 계정을 탈퇴 처리합니다. 되돌릴 수 없습니다.

                    - 카카오 계정은 연결까지 끊습니다. 카카오 '연결된 서비스'에서 우리 앱이 사라집니다
                      (네이버는 사용자 토큰이 필요해 연결 끊기를 지원하지 않고, 우리 데이터만 지웁니다)
                    - 내가 연 행사는 모두 삭제되고, 그 행사의 참가자 전원이 취소·환불됩니다
                    - 내가 낸 신청은 모두 취소되고 결제한 건은 환불됩니다. 비운 자리는 다른 사람이
                      신청할 수 있게 재고로 돌아갑니다
                    - 닉네임·프로필 이미지·소셜 회원번호는 지워집니다. 신청 이력은 정산 추적을 위해 남습니다
                    - 같은 소셜 계정으로 다시 로그인하면 이전 이력이 없는 새 계정으로 가입됩니다
                    - 탈퇴 후에도 기존 액세스 토큰은 만료 전까지 유효합니다. 프론트에서 토큰을 지워야 합니다

                    연결 끊기는 데이터를 지우기 전에 합니다. 502 `LOGIN_PROVIDER_UNLINK_ERROR` 가 오면
                    아직 아무것도 지워지지 않은 상태이므로 같은 요청을 그대로 재시도하면 됩니다.
                    """)
    ResponseEntity<Void> withdraw(@Parameter(hidden = true) @LoginUserId Long userId);
}
