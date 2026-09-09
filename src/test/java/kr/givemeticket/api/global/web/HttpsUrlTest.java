package kr.givemeticket.api.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpsUrlTest {

    @Test
    @DisplayName("http 로 시작하면 https 로 올린다")
    void upgradesHttp() {
        assertThat(HttpsUrl.upgrade("http://img1.kakaocdn.net/thumb/profile.jpg"))
                .isEqualTo("https://img1.kakaocdn.net/thumb/profile.jpg");
    }

    @Test
    @DisplayName("스킴 대문자도 알아본다")
    void upgradesUppercaseScheme() {
        assertThat(HttpsUrl.upgrade("HTTP://img1.kakaocdn.net/a.jpg"))
                .isEqualTo("https://img1.kakaocdn.net/a.jpg");
    }

    @Test
    @DisplayName("이미 https 면 그대로 둔다")
    void keepsHttps() {
        String url = "https://img1.kakaocdn.net/a.jpg";

        assertThat(HttpsUrl.upgrade(url)).isEqualTo(url);
    }

    @Test
    @DisplayName("경로 안의 http 는 건드리지 않는다")
    void onlyTouchesScheme() {
        String url = "https://cdn.example.com/redirect?to=http://other";

        assertThat(HttpsUrl.upgrade(url)).isEqualTo(url);
    }

    @Test
    @DisplayName("없으면 없는 채로 둔다")
    void keepsNull() {
        assertThat(HttpsUrl.upgrade(null)).isNull();
    }
}
