package kr.givemeticket.api.login.infrastructure;

import kr.givemeticket.api.login.domain.AuthCodeCommand;
import kr.givemeticket.api.login.domain.LoginClient;
import kr.givemeticket.api.login.domain.LoginException;
import kr.givemeticket.api.login.domain.LoginProviderException;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.login.infrastructure.dto.NaverProfileResponse;
import kr.givemeticket.api.login.infrastructure.dto.NaverTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 네이버는 OIDC 를 지원하지 않아 id_token 이 없다.
 * 액세스 토큰을 받은 뒤 프로필 API 를 한 번 더 호출해야 회원 식별자를 알 수 있다.
 *
 * <p>그래서 카카오와 달리 {@code IdTokenVerifier} 를 쓰지 않는다.
 * 신원 보증은 서명 검증이 아니라 "방금 우리가 받은 액세스 토큰으로 조회했다"는 사실에서 나온다.
 */
@RequiredArgsConstructor
public class NaverLoginClient implements LoginClient {

    private static final String TOKEN_PATH = "/oauth2.0/token";
    private static final String PROFILE_PATH = "/v1/nid/me";
    private static final String GRANT_TYPE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient authRestClient;
    private final RestClient apiRestClient;
    private final String clientId;
    private final String clientSecret;

    @Override
    public Provider provider() {
        return Provider.NAVER;
    }

    @Override
    public ProviderPrincipal fetchPrincipal(AuthCodeCommand command) {
        String accessToken = requestAccessToken(command);

        return fetchProfile(accessToken);
    }

    private String requestAccessToken(AuthCodeCommand command) {
        if (command.state() == null || command.state().isBlank()) {
            // 카카오만 붙여본 프론트가 가장 자주 놓치는 값이라 제공자에게 보내기 전에 걸러낸다.
            throw LoginException.stateRequired(Provider.NAVER.name());
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", command.code());
        form.add("state", command.state());

        try {
            NaverTokenResponse response = authRestClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(NaverTokenResponse.class);

            if (response == null) {
                throw LoginProviderException.tokenRequestFailed();
            }
            if (response.hasError()) {
                // 200 으로 왔지만 실패다. 대부분 코드 만료·재사용이거나 state 불일치다.
                throw LoginException.invalidAuthorizationCode();
            }
            if (response.accessToken() == null) {
                throw LoginProviderException.tokenRequestFailed();
            }
            return response.accessToken();

        } catch (HttpClientErrorException e) {
            throw LoginException.invalidAuthorizationCode();

        } catch (RestClientException e) {
            throw LoginProviderException.tokenRequestFailed();
        }
    }

    /**
     * 방금 발급받은 토큰으로 부르는 것이라 여기서의 실패는 사용자 잘못이 아니다. 전부 외부 오류로 본다.
     */
    private ProviderPrincipal fetchProfile(String accessToken) {
        try {
            NaverProfileResponse response = apiRestClient.get()
                    .uri(PROFILE_PATH)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .retrieve()
                    .body(NaverProfileResponse.class);

            if (response == null || !response.isSuccess()) {
                throw LoginProviderException.profileRequestFailed();
            }

            NaverProfileResponse.Profile profile = response.response();
            return new ProviderPrincipal(
                    profile.id(), Provider.NAVER, profile.nickname(), profile.profileImage());

        } catch (RestClientException e) {
            throw LoginProviderException.profileRequestFailed();
        }
    }
}
