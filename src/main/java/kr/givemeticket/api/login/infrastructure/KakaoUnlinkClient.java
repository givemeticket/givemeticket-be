package kr.givemeticket.api.login.infrastructure;

import kr.givemeticket.api.login.domain.LoginProviderException;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderUnlinkClient;
import kr.givemeticket.api.login.infrastructure.dto.KakaoApiErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 연결 끊기(<code>POST /v1/user/unlink</code>).
 *
 * <p>이 API 는 사용자의 액세스 토큰이나 어드민 키 중 하나로 부를 수 있는데, 여기서는 어드민 키를 쓴다.
 * 로그인 때 우리가 검증하는 건 id_token 뿐이고 액세스 토큰은 그 자리에서 버리기 때문에,
 * 한참 뒤인 탈퇴 시점에는 사용자 토큰이 남아 있지 않다. 어드민 키 + 회원번호 조합만이
 * 사용자 없이 부를 수 있는 경로다.
 *
 * <p>어드민 키는 앱 전체를 대리하는 키다. 로그인용 REST API 키와 달리 절대 클라이언트로 내려가면 안 되고,
 * 로그에도 남기지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class KakaoUnlinkClient implements ProviderUnlinkClient {

    private static final String UNLINK_PATH = "/v1/user/unlink";
    private static final String ADMIN_KEY_PREFIX = "KakaoAK ";
    private static final String TARGET_ID_TYPE = "user_id";

    private final RestClient restClient;
    private final String adminKey;

    @Override
    public Provider provider() {
        return Provider.KAKAO;
    }

    @Override
    public void unlink(String providerId) {
        if (adminKey == null || adminKey.isBlank()) {
            // 어드민 키 없이도 탈퇴 자체는 되게 둔다. 카카오 앱을 붙이지 않은 로컬 개발에서 막히지 않도록.
            // 운영에서 이 로그가 보이면 KAKAO_ADMIN_KEY 가 빠진 것이고, 연결이 남는다.
            log.warn("kakao unlink skipped: admin key is not configured");
            return;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", TARGET_ID_TYPE);
        form.add("target_id", providerId);

        try {
            restClient.post()
                    .uri(UNLINK_PATH)
                    .header(HttpHeaders.AUTHORIZATION, ADMIN_KEY_PREFIX + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException e) {
            handleRejection(e);

        } catch (RestClientException e) {
            // 5xx·타임아웃. 끊겼는지 알 수 없으니 실패로 두고 재시도에 맡긴다.
            throw LoginProviderException.unlinkFailed();
        }
    }

    /**
     * 이미 끊긴 계정은 성공으로 본다. 탈퇴가 중간에 실패해 다시 들어온 요청이 여기서 막히면
     * 사용자가 영영 탈퇴하지 못한다.
     */
    private void handleRejection(HttpClientErrorException e) {
        KakaoApiErrorResponse error = e.getResponseBodyAs(KakaoApiErrorResponse.class);

        if (error != null && error.isNotLinked()) {
            log.info("kakao unlink skipped: already unlinked");
            return;
        }

        log.warn("kakao unlink rejected: status={}, code={}, msg={}", e.getStatusCode().value(),
                error == null ? null : error.code(), error == null ? null : error.msg());

        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
            // 어드민 키가 틀렸거나 권한이 없다. 재시도해도 달라지지 않으니 설정을 의심하게 만든다.
            throw LoginProviderException.misconfigured();
        }
        throw LoginProviderException.unlinkFailed();
    }
}
