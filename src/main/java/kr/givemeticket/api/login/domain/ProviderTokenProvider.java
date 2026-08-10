package kr.givemeticket.api.login.domain;

import io.jsonwebtoken.Claims;
import java.util.Map;
import kr.givemeticket.api.global.auth.AuthException;
import kr.givemeticket.api.global.auth.JwtProvider;
import kr.givemeticket.api.global.auth.TokenType;
import kr.givemeticket.api.login.ui.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 소셜 인증은 끝났지만 아직 우리 서비스의 유저는 아닌 상태를 담는 토큰.
 * 로그인·회원가입에만 쓸 수 있고, 그 사이 시간만 버티면 되므로 만료를 짧게 잡는다.
 */
@Component
public class ProviderTokenProvider {

    private static final String PROVIDER_CLAIM = "provider";

    private final JwtProvider jwtProvider;
    private final long expirationMillis;

    public ProviderTokenProvider(JwtProvider jwtProvider,
                                 @Value("${jwt.provider-token.expiration}") long expirationMillis) {
        this.jwtProvider = jwtProvider;
        this.expirationMillis = expirationMillis;
    }

    public TokenResponse createToken(ProviderPrincipal providerPrincipal) {
        return TokenResponse.from(jwtProvider.createToken(
                TokenType.PROVIDER, providerPrincipal.providerId(), expirationMillis,
                Map.of(PROVIDER_CLAIM, providerPrincipal.provider().name())));
    }

    public ProviderPrincipal extractPrincipal(String token) {
        Claims claims = jwtProvider.parseClaims(token, TokenType.PROVIDER);

        String providerId = claims.getSubject();
        String provider = claims.get(PROVIDER_CLAIM, String.class);
        if (providerId == null || providerId.isBlank() || provider == null) {
            throw AuthException.invalidToken();
        }

        return new ProviderPrincipal(providerId, Provider.from(provider));
    }
}
