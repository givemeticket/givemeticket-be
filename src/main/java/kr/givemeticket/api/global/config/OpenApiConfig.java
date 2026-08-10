package kr.givemeticket.api.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("givemeticket API")
                        .version("v1")
                        .description("선착순 티켓 신청 API. 로그인 API로 받은 액세스 토큰을 "
                                + "Authorization: Bearer 헤더에 담아 호출합니다."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // 로그인·회원가입은 각 명세에서 @SecurityRequirements 로 이 기본값을 비운다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
