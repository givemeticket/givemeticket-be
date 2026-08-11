package kr.givemeticket.api.login.infrastructure;

import kr.givemeticket.api.login.domain.AuthCodeCommand;
import kr.givemeticket.api.login.domain.IdTokenVerifier;
import kr.givemeticket.api.login.domain.LoginClient;
import kr.givemeticket.api.login.domain.LoginException;
import kr.givemeticket.api.login.domain.LoginProviderException;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.login.infrastructure.dto.KakaoTokenResponse;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class KakaoLoginClient implements LoginClient {

    private static final String TOKEN_PATH = "/oauth/token";
    private static final String GRANT_TYPE = "authorization_code";

    private final RestClient restClient;
    private final IdTokenVerifier idTokenVerifier;
    private final String restApiKey;

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
            // 4xx 는 코드가 만료됐거나 redirect_uri 가 등록된 값과 다른 경우다. 우리 잘못이 아니라 요청 문제다.
            throw LoginException.invalidAuthorizationCode();

        } catch (RestClientException e) {
            throw LoginProviderException.tokenRequestFailed();
        }
    }
}
