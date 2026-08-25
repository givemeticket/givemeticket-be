package kr.givemeticket.api.login.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import kr.givemeticket.api.login.domain.LoginProviderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class KakaoUnlinkClientTest {

    private static final String BASE_URL = "https://kapi.test";
    private static final String UNLINK_URL = BASE_URL + "/v1/user/unlink";
    private static final String ADMIN_KEY = "admin-key";
    private static final String PROVIDER_ID = "1234567890";

    private MockRestServiceServer server;

    private KakaoUnlinkClient client(String adminKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        this.server = MockRestServiceServer.bindTo(builder).build();

        return new KakaoUnlinkClient(builder.build(), adminKey);
    }

    @Test
    @DisplayName("어드민 키와 회원번호로 연결 끊기를 요청한다")
    void requestsUnlinkWithAdminKey() {
        KakaoUnlinkClient client = client(ADMIN_KEY);

        MultiValueMap<String, String> expected = new LinkedMultiValueMap<>();
        expected.add("target_id_type", "user_id");
        expected.add("target_id", PROVIDER_ID);

        server.expect(requestTo(UNLINK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK " + ADMIN_KEY))
                .andExpect(content().formData(expected))
                .andRespond(withSuccess("{\"id\":" + PROVIDER_ID + "}", MediaType.APPLICATION_JSON));

        client.unlink(PROVIDER_ID);

        server.verify();
    }

    /**
     * 탈퇴가 중간에 끊겨 다시 들어온 요청이 여기서 막히면 사용자가 영영 탈퇴하지 못한다.
     */
    @Test
    @DisplayName("이미 연결이 끊긴 계정은 성공으로 본다")
    void treatsAlreadyUnlinkedAsSuccess() {
        KakaoUnlinkClient client = client(ADMIN_KEY);

        server.expect(requestTo(UNLINK_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"not linked user\",\"code\":-101}"));

        assertThatCode(() -> client.unlink(PROVIDER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("어드민 키가 거부되면 설정 문제로 알린다")
    void reportsMisconfiguredWhenAdminKeyRejected() {
        KakaoUnlinkClient client = client("wrong-key");

        server.expect(requestTo(UNLINK_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"invalid app key\",\"code\":-401}"));

        assertThatThrownBy(() -> client.unlink(PROVIDER_ID))
                .isInstanceOf(LoginProviderException.class)
                .hasFieldOrPropertyWithValue("code", "LOGIN_PROVIDER_MISCONFIGURED");
    }

    @Test
    @DisplayName("카카오 장애는 재시도할 수 있는 실패로 남긴다")
    void reportsUnlinkFailedOnProviderError() {
        KakaoUnlinkClient client = client(ADMIN_KEY);

        server.expect(requestTo(UNLINK_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.unlink(PROVIDER_ID))
                .isInstanceOf(LoginProviderException.class)
                .hasFieldOrPropertyWithValue("code", "LOGIN_PROVIDER_UNLINK_ERROR");
    }

    /**
     * 카카오 앱을 붙이지 않은 로컬 개발에서 탈퇴 자체가 막히지 않아야 한다.
     */
    @Test
    @DisplayName("어드민 키가 없으면 호출하지 않고 넘어간다")
    void skipsWhenAdminKeyIsMissing() {
        KakaoUnlinkClient client = client("");

        client.unlink(PROVIDER_ID);

        server.verify();
    }
}
