package kr.givemeticket.api.login.infrastructure;

import kr.givemeticket.api.login.domain.LoginClient;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NaverLoginConfig {

    /**
     * 토큰 발급과 프로필 조회의 호스트가 달라 RestClient 를 두 개 만든다.
     */
    @Bean
    public LoginClient naverLoginClient(NaverLoginProperties properties) {
        return new NaverLoginClient(
                naverRestClient(properties, properties.authBaseUrl()),
                naverRestClient(properties, properties.apiBaseUrl()),
                properties.clientId(),
                properties.clientSecret());
    }

    private RestClient naverRestClient(NaverLoginProperties properties, String baseUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
