package kr.givemeticket.api.login.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 회원 정보는 한 겹 안쪽 response 에 들어 있고, 성공 여부는 resultcode 로 온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverProfileResponse(

        @JsonProperty("resultcode")
        String resultCode,

        @JsonProperty("message")
        String message,

        @JsonProperty("response")
        Profile response
) {

    private static final String SUCCESS_CODE = "00";

    /**
     * nickname 과 profileImage 는 선택 동의 항목이라 비어 올 수 있다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(

            @JsonProperty("id")
            String id,

            @JsonProperty("nickname")
            String nickname,

            @JsonProperty("profile_image")
            String profileImage
    ) {

    }

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(resultCode) && response != null && response.id() != null;
    }
}
