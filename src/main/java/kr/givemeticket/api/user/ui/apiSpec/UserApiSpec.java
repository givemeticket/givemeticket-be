package kr.givemeticket.api.user.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import org.springframework.http.ResponseEntity;

@Tag(name = "유저 API", description = "내 계정 관련 API 명세입니다.")
public interface UserApiSpec {

    @Operation(summary = "회원 탈퇴",
            description = """
                    내 계정을 탈퇴 처리합니다. 되돌릴 수 없습니다.

                    - 내가 연 행사는 모두 삭제되고, 그 행사의 참가자 전원이 취소·환불됩니다
                    - 내가 낸 신청은 모두 취소되고 결제한 건은 환불됩니다. 비운 자리는 다른 사람이
                      신청할 수 있게 재고로 돌아갑니다
                    - 닉네임·프로필 이미지·소셜 회원번호는 지워집니다. 신청 이력은 정산 추적을 위해 남습니다
                    - 같은 소셜 계정으로 다시 로그인하면 이전 이력이 없는 새 계정으로 가입됩니다
                    - 탈퇴 후에도 기존 액세스 토큰은 만료 전까지 유효합니다. 프론트에서 토큰을 지워야 합니다
                    """)
    ResponseEntity<Void> withdraw(@Parameter(hidden = true) @LoginUserId Long userId);
}
