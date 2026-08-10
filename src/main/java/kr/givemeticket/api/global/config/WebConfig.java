package kr.givemeticket.api.global.config;

import java.time.Duration;
import java.util.List;
import kr.givemeticket.api.global.auth.LoginUserIdArgumentResolver;
import kr.givemeticket.api.global.auth.ProviderArgumentResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String API_PREFIX = "/api/v1";

    private final LoginUserIdArgumentResolver loginUserIdArgumentResolver;
    private final ProviderArgumentResolver providerArgumentResolver;
    private final String[] corsAllowedOrigins;
    private final Duration corsMaxAge;

    public WebConfig(
            LoginUserIdArgumentResolver loginUserIdArgumentResolver,
            ProviderArgumentResolver providerArgumentResolver,
            @Value("${app.cors.allowed-origins}") String[] corsAllowedOrigins,
            @Value("${app.cors.max-age}") Duration corsMaxAge
    ) {
        if (corsAllowedOrigins.length == 0) {
            // 빈 목록으로 두면 브라우저 요청만 전부 막히고 Postman 은 멀쩡해서 원인을 찾기 어렵다.
            throw new IllegalStateException("app.cors.allowed-origins 가 비어 있습니다.");
        }
        this.loginUserIdArgumentResolver = loginUserIdArgumentResolver;
        this.providerArgumentResolver = providerArgumentResolver;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.corsMaxAge = corsMaxAge;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                HandlerTypePredicate.forBasePackage("kr.givemeticket.api"));
    }

    /**
     * 오리진을 목록으로 못 박는다. 와일드카드로 열어두면 아무 사이트나 사용자의 브라우저를 빌려
     * 이 API 를 호출할 수 있다. 목록은 application.yml 이 유일한 출처다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsAllowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                // 201 응답의 Location 은 기본 노출 대상이 아니라, 명시하지 않으면 프론트가 읽지 못한다.
                .exposedHeaders(HttpHeaders.LOCATION)
                // 토큰을 Authorization 헤더로 보내므로 쿠키를 주고받을 이유가 없다.
                .allowCredentials(false)
                .maxAge(corsMaxAge.toSeconds());
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserIdArgumentResolver);
        resolvers.add(providerArgumentResolver);
    }
}
