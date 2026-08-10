package kr.givemeticket.api.login.ui.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import kr.givemeticket.api.login.domain.AuthCodeCommand;
import kr.givemeticket.api.login.domain.Provider;

public record AuthCodeRequest(

        @Schema(description = "소셜 로그인 후 받은 인가 코드", example = "aBcDeF...")
        @NotBlank(message = "코드는 공백일 수 없습니다.")
        String code,

        @Schema(description = "로그인 방식", example = "kakao", allowableValues = {"kakao", "naver"})
        @NotBlank(message = "로그인 방식은 공백일 수 없습니다.")
        String provider,

        @Schema(description = "인가 코드를 받을 때 쓴 값과 같아야 합니다. 카카오에서만 씁니다.",
                example = "https://givemeticket.kr/oauth/kakao")
        @NotBlank(message = "redirectUrl은 공백일 수 없습니다.")
        String redirectUrl,

        @Schema(description = "인가 코드를 받을 때 쓴 CSRF 방지 값. 네이버는 필수, 카카오는 무시됩니다.",
                example = "RANDOM_STATE")
        String state
) {

    public AuthCodeCommand toAuthCodeCommand() {
        return new AuthCodeCommand(Provider.from(provider), code, redirectUrl, state);
    }
}
