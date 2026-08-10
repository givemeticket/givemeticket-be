package kr.givemeticket.api.global.auth;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 가입이 끝난 사용자의 API 접근 토큰을 발급하고 다시 userId 로 되돌린다.
 */
@Component
public class AccessTokenProvider {

    private final JwtProvider jwtProvider;
    private final long expirationMillis;

    public AccessTokenProvider(JwtProvider jwtProvider,
                               @Value("${jwt.access-token.expiration}") long expirationMillis) {
        this.jwtProvider = jwtProvider;
        this.expirationMillis = expirationMillis;
    }

    public String createToken(Long userId) {
        return jwtProvider.createToken(TokenType.ACCESS, String.valueOf(userId), expirationMillis);
    }

    public Long extractUserId(String token) {
        Claims claims = jwtProvider.parseClaims(token, TokenType.ACCESS);

        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw AuthException.invalidToken();
        }
    }
}
