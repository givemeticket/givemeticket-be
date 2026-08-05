package kr.givemeticket.api.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("givemeticket API")
                .version("v1")
                .description("선착순 티켓 신청 API. 인증은 범위 밖이며 X-User-Id 헤더 값을 유저 식별자로 사용합니다."));
    }
}
