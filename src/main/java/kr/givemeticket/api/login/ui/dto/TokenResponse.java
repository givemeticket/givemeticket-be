package kr.givemeticket.api.login.ui.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(

        @Schema(description = "Authorization 헤더에 'Bearer {token}' 형태로 담아 보냅니다.")
        String token
) {

    public static TokenResponse from(String token) {
        return new TokenResponse(token);
    }
}
