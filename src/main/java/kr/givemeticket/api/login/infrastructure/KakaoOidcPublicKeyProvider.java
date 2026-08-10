package kr.givemeticket.api.login.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPublicKey;
import kr.givemeticket.api.login.domain.LoginException;
import kr.givemeticket.api.login.domain.OidcPublicKeyProvider;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class KakaoOidcPublicKeyProvider implements OidcPublicKeyProvider {

    private final KakaoJwksClient kakaoJwksClient;
    private final KakaoJwksCache kakaoJwksCache;

    /**
     * 갱신 후에도 모르는 kid 라면 JWKS 조회 실패가 아니라 토큰이 우리가 아는 키로 서명되지 않은 것이다.
     * 위조 토큰마다 502를 남기지 않도록 401로 돌려준다.
     */
    @Override
    public RSAPublicKey getPublicKey(String keyId) {
        JWK jwk = kakaoJwksCache.findKey(keyId, kakaoJwksClient::fetchJwkSet);

        if (!(jwk instanceof RSAKey rsaKey)) {
            throw LoginException.invalidIdToken();
        }

        try {
            return rsaKey.toRSAPublicKey();
        } catch (JOSEException e) {
            throw LoginException.invalidIdToken();
        }
    }
}
