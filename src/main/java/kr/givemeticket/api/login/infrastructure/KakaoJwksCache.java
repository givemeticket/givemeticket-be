package kr.givemeticket.api.login.infrastructure;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * 카카오 공개키를 TTL 동안 들고 있는다. ID 토큰을 검증할 때마다 JWKS 를 조회하지 않기 위한 캐시다.
 *
 * <p>메서드 전체를 잠그므로 캐시가 비었을 때 여러 요청이 몰려도 조회는 한 번만 나간다.
 * 대신 그동안 다른 로그인 요청은 대기한다 — 캐시 히트가 대부분이라 감수할 만한 비용이다.
 */
public class KakaoJwksCache {

    /**
     * 모르는 kid 로 JWKS 조회를 무한정 유발하는 것을 막는 최소 재조회 간격.
     */
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(30);

    private final Duration ttl;

    private JWKSet cachedJwkSet;
    private Instant expiresAt;
    private Instant lastRefreshedAt;

    public KakaoJwksCache(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * @return kid 에 해당하는 공개키. 갱신해도 없으면 null
     */
    public synchronized JWK findKey(String keyId, Supplier<JWKSet> loader) {
        if (isExpired()) {
            refresh(loader);
        }

        JWK jwk = keyOf(keyId);
        if (jwk == null && isRefreshable()) {
            // TTL 이 남아 있어도 그 사이 키가 교체됐을 수 있다. 모르는 kid 를 만나면 한 번 더 받아본다.
            refresh(loader);
            jwk = keyOf(keyId);
        }
        return jwk;
    }

    private void refresh(Supplier<JWKSet> loader) {
        JWKSet jwkSet = loader.get();
        Instant now = Instant.now();

        this.cachedJwkSet = jwkSet;
        this.expiresAt = now.plus(ttl);
        this.lastRefreshedAt = now;
    }

    private boolean isExpired() {
        return cachedJwkSet == null || expiresAt == null || Instant.now().isAfter(expiresAt);
    }

    private boolean isRefreshable() {
        return lastRefreshedAt == null
                || Instant.now().isAfter(lastRefreshedAt.plus(MIN_REFRESH_INTERVAL));
    }

    private JWK keyOf(String keyId) {
        return (cachedJwkSet == null) ? null : cachedJwkSet.getKeyByKeyId(keyId);
    }
}
