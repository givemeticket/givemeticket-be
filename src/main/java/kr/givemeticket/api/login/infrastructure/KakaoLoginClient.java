package kr.givemeticket.api.login.infrastructure;

import kr.givemeticket.api.login.domain.AuthCodeCommand;
import kr.givemeticket.api.login.domain.IdTokenVerifier;
import kr.givemeticket.api.login.domain.LoginClient;
import kr.givemeticket.api.login.domain.LoginException;
import kr.givemeticket.api.login.domain.LoginProviderException;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.login.infrastructure.dto.KakaoErrorResponse;
import kr.givemeticket.api.login.infrastructure.dto.KakaoTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오는 OIDC 를 지원하므로 토큰 응답의 id_token 만 검증하면 된다.
 * 닉네임과 프로필 이미지도 그 안에 들어 있어 프로필 API 를 따로 부르지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class KakaoLoginClient implements LoginClient {

    private static final String TOKEN_PATH = "/oauth/token";
    private static final String GRANT_TYPE = "authorization_code";

    private final RestClient restClient;
    private final IdTokenVerifier idTokenVerifier;
    private final String restApiKey;
    private final String clientSecret;

    @Override
    public Provider provider() {
        return Provider.KAKAO;
    }

    @Override
    public ProviderPrincipal fetchPrincipal(AuthCodeCommand command) {
        KakaoTokenResponse response = requestToken(command);

        return idTokenVerifier.extractPrincipal(response.idToken());
    }

    /**
     * 인가 코드는 쿼리스트링이 아니라 form body 로 보낸다.
     * URL 인코딩을 직접 신경 쓸 일이 없고, 1회용 코드가 접근 로그에 남지도 않는다.
     */
    private KakaoTokenResponse requestToken(AuthCodeCommand command) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("client_id", restApiKey);
        form.add("redirect_uri", command.redirectUrl());
        form.add("code", command.code());

        if (clientSecret != null && !clientSecret.isBlank()) {
            // 콘솔에서 켠 앱만 보내야 한다. 끄고 보내도, 켜고 안 보내도 invalid_client 가 된다.
            form.add("client_secret", clientSecret);
        }

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (response == null || response.idToken() == null) {
                // 200 인데 id_token 이 없다. OpenID Connect 활성화가 빠졌을 때 이렇게 온다.
                throw LoginProviderException.tokenRequestFailed();
            }
            return response;

        } catch (HttpClientErrorException e) {
            throw translateRejection(e);

        } catch (RestClientException e) {
            throw LoginProviderException.tokenRequestFailed();
        }
    }

    /**
     * 카카오가 거절한 이유를 남기고 나눈다. 앱 키가 틀린 것과 인가 코드가 만료된 것은
     * 손댈 곳이 완전히 다른데, 둘 다 4xx 라 묶어 두면 설정을 의심하지 못한다.
     */
    private RuntimeException translateRejection(HttpClientErrorException e) {
        KakaoErrorResponse error = e.getResponseBodyAs(KakaoErrorResponse.class);

        if (error == null) {
            log.warn("kakao token request rejected without body: status={}", e.getStatusCode().value());
            return LoginException.invalidAuthorizationCode();
        }

        log.warn("kakao token request rejected: status={}, error={}, errorCode={}, description={}",
                e.getStatusCode().value(), error.error(), error.errorCode(), error.errorDescription());

        if (error.isClientMisconfigured()) {
            return LoginProviderException.misconfigured();
        }
        return LoginException.invalidAuthorizationCode();
    }
}
