package kr.givemeticket.api.login.infrastructure;

import com.nimbusds.jose.jwk.JWKSet;
import java.text.ParseException;
import kr.givemeticket.api.login.domain.LoginProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
public class KakaoJwksClient {

    private final RestClient restClient;
    private final String jwksPath;

    public JWKSet fetchJwkSet() {
        try {
            String body = restClient.get()
                    .uri(jwksPath)
                    .retrieve()
                    .body(String.class);

            if (body == null) {
                throw LoginProviderException.publicKeyRequestFailed();
            }
            return JWKSet.parse(body);

        } catch (RestClientException | ParseException e) {
            throw LoginProviderException.publicKeyRequestFailed();
        }
    }
}
