package kr.givemeticket.api.login.ui.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @Schema(description = "표시할 닉네임", example = "민기")
        @NotBlank(message = "닉네임은 공백일 수 없습니다.")
        @Size(max = 20, message = "닉네임은 20자를 넘을 수 없습니다.")
        String nickname,

        @Schema(description = "프로필 이미지 URL. 소셜 프로필에서 가져온 값을 그대로 보내면 됩니다. 선택입니다.",
                example = "https://k.kakaocdn.net/dn/.../profile.jpg")
        @Size(max = 500, message = "프로필 이미지 URL은 500자를 넘을 수 없습니다.")
        String profileImageUrl
) {

}
