package kr.givemeticket.api.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 우리 서비스가 발급하는 JWT 의 생성과 검증만 담당한다.
 * 어떤 용도의 토큰인지는 {@link TokenType} 클레임이 구분하고, 용도별 만료·클레임은 위 계층이 정한다.
 */
@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final SecretKey secretKey;

    public JwtProvider(@Value("${jwt.secret-key}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(TokenType tokenType, String subject, long expirationMillis) {
        return createToken(tokenType, subject, expirationMillis, Map.of());
    }

    public String createToken(TokenType tokenType, String subject, long expirationMillis,
                              Map<String, Object> extraClaims) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder();

        if (!extraClaims.isEmpty()) {
            builder.claims(extraClaims);
        }

        // 예약 클레임을 뒤에 세팅해 extraClaims 가 sub/iat/exp 를 덮어쓰지 못하게 한다.
        return builder
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 서명·만료와 함께 토큰 용도까지 확인한다.
     * 가입 전 임시 토큰으로 일반 API 를 호출하는 것을 여기서 막는다.
     */
    public Claims parseClaims(String token, TokenType expected) {
        Claims claims = parseClaims(token);

        if (!expected.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw AuthException.tokenTypeMismatch();
        }
        return claims;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw AuthException.expiredToken();
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException 은 토큰이 비어 있거나 형식 자체가 아닌 경우다.
            throw AuthException.invalidToken();
        }
    }
}
