package kr.givemeticket.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class CorsConfigTest {

    /**
     * WebConfig 가 실제로 등록하는 CorsConfiguration 을 꺼내 본다.
     * getCorsConfigurations() 가 protected 라 테스트에서만 열어 쓴다.
     */
    private static class InspectableCorsRegistry extends CorsRegistry {

        @Override
        protected Map<String, CorsConfiguration> getCorsConfigurations() {
            return super.getCorsConfigurations();
        }
    }

    private CorsConfiguration configure(String... allowedOrigins) {
        InspectableCorsRegistry registry = new InspectableCorsRegistry();
        new WebConfig(null, null, allowedOrigins, Duration.ofHours(1)).addCorsMappings(registry);
        return registry.getCorsConfigurations().get("/**");
    }

    @Test
    @DisplayName("설정한 오리진만 허용하고 그 외에는 막는다")
    void allowsOnlyConfiguredOrigins() {
        CorsConfiguration config = configure("https://givemeticket.site", "http://localhost:5173");

        assertThat(config.checkOrigin("https://givemeticket.site")).isNotNull();
        assertThat(config.checkOrigin("http://localhost:5173")).isNotNull();

        assertThat(config.checkOrigin("https://evil.example.com")).isNull();
        // 스킴과 포트도 정확히 일치해야 한다
        assertThat(config.checkOrigin("http://givemeticket.site")).isNull();
        assertThat(config.checkOrigin("http://localhost:3000")).isNull();
    }

    @Test
    @DisplayName("허용 오리진이 비면 부팅 단계에서 걸린다")
    void rejectsEmptyAllowedOrigins() {
        // 빈 목록으로 뜨면 브라우저 요청만 조용히 전부 막힌다
        assertThatThrownBy(() -> new WebConfig(null, null, new String[0], Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.cors.allowed-origins");
    }

    @Test
    @DisplayName("프리플라이트가 Authorization 헤더와 필요한 메서드를 통과시킨다")
    void allowsAuthorizationHeaderAndMethods() {
        CorsConfiguration config = configure("https://givemeticket.site");

        assertThat(config.checkHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)))
                .isNotNull();
        assertThat(config.checkHttpMethod(HttpMethod.PATCH)).isNotNull();
        assertThat(config.checkHttpMethod(HttpMethod.DELETE)).isNotNull();
    }

    @Test
    @DisplayName("Location 을 노출하고 쿠키는 허용하지 않는다")
    void exposesLocationWithoutCredentials() {
        CorsConfiguration config = configure("https://givemeticket.site");

        // 캠페인·신청 생성이 201 + Location 으로 응답한다
        assertThat(config.getExposedHeaders()).contains(HttpHeaders.LOCATION);
        assertThat(config.getAllowCredentials()).isFalse();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }
}
