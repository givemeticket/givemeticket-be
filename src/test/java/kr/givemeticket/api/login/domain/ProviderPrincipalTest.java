package kr.givemeticket.api.login.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderPrincipalTest {

    /**
     * 카카오는 프로필 사진이 없는 계정에 기본 이미지를 http 로 내려준다.
     * 그대로 저장하면 https 페이지에서 Mixed Content 경고가 된다.
     */
    @Test
    @DisplayName("프로필 이미지가 http 면 https 로 올려 담는다")
    void upgradesProfileImageUrl() {
        ProviderPrincipal principal = new ProviderPrincipal(
                "123456", Provider.KAKAO, "민기", "http://img1.kakaocdn.net/thumb/default.jpg");

        assertThat(principal.profileImageUrl())
                .isEqualTo("https://img1.kakaocdn.net/thumb/default.jpg");
    }

    @Test
    @DisplayName("동의하지 않아 이미지가 없어도 그대로 비어 있다")
    void keepsMissingProfileImageUrl() {
        ProviderPrincipal principal = new ProviderPrincipal(
                "123456", Provider.KAKAO, "민기", null);

        assertThat(principal.profileImageUrl()).isNull();
    }
}
