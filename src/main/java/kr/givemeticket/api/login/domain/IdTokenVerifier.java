package kr.givemeticket.api.login.domain;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * OIDC ID 토큰을 검증하고 신원을 꺼낸다.
 *
 * <p>서명만 확인하면 같은 제공자의 <b>다른 앱</b>에서 발급된 토큰도 통과한다.
 * 발급자(iss)와 대상(aud)까지 함께 봐야 우리 앱을 향해 발급된 토큰임이 보장된다.
 *
 * <p>닉네임과 프로필 이미지도 토큰 안에 있어서 프로필 API 를 따로 부르지 않는다.
 * 동의 항목이 꺼져 있으면 비어 오는데, 그 처리는 {@link ProviderPrincipal} 이 맡는다.
 */
@RequiredArgsConstructor
public class IdTokenVerifier {

    private static final JWSAlgorithm REQUIRED_ALGORITHM = JWSAlgorithm.RS256;
    private static final String NICKNAME_CLAIM = "nickname";
    private static final String PICTURE_CLAIM = "picture";

    /**
     * 서버 간 시계 차이를 감안해 만료 판정에 두는 여유.
     */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final OidcPublicKeyProvider publicKeyProvider;
    private final Provider provider;
    private final String issuer;

    /**
     * ID 토큰의 aud 로 와야 하는 값. 카카오는 앱의 REST API 키다.
     */
    private final String audience;

    public ProviderPrincipal extractPrincipal(String idToken) {
        SignedJWT signedJwt = parse(idToken);
        verifySignature(signedJwt);

        JWTClaimsSet claims = extractClaims(signedJwt);
        verifyIssuer(claims);
        verifyAudience(claims);
        verifyNotExpired(claims);

        return new ProviderPrincipal(
                extractSubject(claims),
                provider,
                stringClaim(claims, NICKNAME_CLAIM),
                stringClaim(claims, PICTURE_CLAIM));
    }

    private SignedJWT parse(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw LoginException.invalidIdToken();
        }
        try {
            return SignedJWT.parse(idToken);
        } catch (ParseException e) {
            throw LoginException.invalidIdToken();
        }
    }

    private void verifySignature(SignedJWT signedJwt) {
        if (!REQUIRED_ALGORITHM.equals(signedJwt.getHeader().getAlgorithm())) {
            // alg 를 고정하지 않으면 none 이나 대칭키 알고리즘으로 우회당할 수 있다.
            throw LoginException.invalidIdToken();
        }

        String keyId = signedJwt.getHeader().getKeyID();
        if (keyId == null || keyId.isBlank()) {
            throw LoginException.invalidIdToken();
        }

        RSAPublicKey publicKey = publicKeyProvider.getPublicKey(keyId);
        try {
            if (!signedJwt.verify(new RSASSAVerifier(publicKey))) {
                throw LoginException.invalidIdToken();
            }
        } catch (JOSEException e) {
            throw LoginException.invalidIdToken();
        }
    }

    private JWTClaimsSet extractClaims(SignedJWT signedJwt) {
        try {
            return signedJwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw LoginException.invalidIdToken();
        }
    }

    private void verifyIssuer(JWTClaimsSet claims) {
        if (!issuer.equals(claims.getIssuer())) {
            throw LoginException.invalidIdToken();
        }
    }

    private void verifyAudience(JWTClaimsSet claims) {
        List<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(audience)) {
            throw LoginException.invalidIdToken();
        }
    }

    private void verifyNotExpired(JWTClaimsSet claims) {
        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.toInstant().plus(CLOCK_SKEW).isBefore(Instant.now())) {
            throw LoginException.expiredIdToken();
        }
    }

    private String extractSubject(JWTClaimsSet claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw LoginException.invalidIdToken();
        }
        return subject;
    }

    /**
     * 프로필 클레임은 없어도 로그인은 되어야 한다. 타입이 어긋나도 값이 없는 것으로 본다.
     */
    private String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException e) {
            return null;
        }
    }
}
