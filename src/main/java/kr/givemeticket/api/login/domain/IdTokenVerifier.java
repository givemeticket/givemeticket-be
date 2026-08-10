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

@RequiredArgsConstructor
public class IdTokenVerifier {

    private static final JWSAlgorithm REQUIRED_ALGORITHM = JWSAlgorithm.RS256;

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final OidcPublicKeyProvider publicKeyProvider;
    private final String issuer;

    private final String audience;

    public String extractProviderId(String idToken) {
        SignedJWT signedJwt = parse(idToken);
        verifySignature(signedJwt);

        JWTClaimsSet claims = extractClaims(signedJwt);
        verifyIssuer(claims);
        verifyAudience(claims);
        verifyNotExpired(claims);

        return extractSubject(claims);
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
}
