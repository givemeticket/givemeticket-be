package kr.givemeticket.api.login.infrastructure;

import kr.givemeticket.api.login.domain.IdTokenVerifier;
import kr.givemeticket.api.login.domain.LoginClient;
import kr.givemeticket.api.login.domain.OidcPublicKeyProvider;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderUnlinkClient;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoLoginConfig {

    @Bean
    public LoginClient kakaoLoginClient(KakaoLoginProperties properties,
                                        IdTokenVerifier kakaoIdTokenVerifier) {
        return new KakaoLoginClient(kakaoRestClient(properties, properties.baseUrl()), kakaoIdTokenVerifier,
                properties.restApiKey(), properties.clientSecret());
    }

    /**
     * 연결 끊기는 인증 서버(kauth)가 아니라 API 서버(kapi)로 간다.
     */
    @Bean
    public ProviderUnlinkClient kakaoUnlinkClient(KakaoLoginProperties properties) {
        return new KakaoUnlinkClient(
                kakaoRestClient(properties, properties.apiBaseUrl()), properties.adminKey());
    }

    @Bean
    public IdTokenVerifier kakaoIdTokenVerifier(OidcPublicKeyProvider kakaoOidcPublicKeyProvider,
                                                KakaoLoginProperties properties) {
        // 카카오는 앱의 REST API 키를 그대로 ID 토큰의 aud 로 넣어준다.
        return new IdTokenVerifier(kakaoOidcPublicKeyProvider, Provider.KAKAO,
                properties.issuer(), properties.restApiKey());
    }

    @Bean
    public OidcPublicKeyProvider kakaoOidcPublicKeyProvider(KakaoJwksClient kakaoJwksClient,
                                                            KakaoJwksCache kakaoJwksCache) {
        return new KakaoOidcPublicKeyProvider(kakaoJwksClient, kakaoJwksCache);
    }

    @Bean
    public KakaoJwksCache kakaoJwksCache(KakaoLoginProperties properties) {
        return new KakaoJwksCache(properties.jwksCacheTtl());
    }

    @Bean
    public KakaoJwksClient kakaoJwksClient(KakaoLoginProperties properties) {
        return new KakaoJwksClient(kakaoRestClient(properties, properties.baseUrl()), properties.jwksPath());
    }

    private RestClient kakaoRestClient(KakaoLoginProperties properties, String baseUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
