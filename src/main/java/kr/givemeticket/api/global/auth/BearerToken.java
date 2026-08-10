package kr.givemeticket.api.global.auth;

/**
 * Authorization 헤더에서 Bearer 토큰만 꺼낸다.
 */
public final class BearerToken {

    private static final String PREFIX = "Bearer ";

    private BearerToken() {
    }

    public static String resolve(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw AuthException.missingToken();
        }
        if (!authorizationHeader.startsWith(PREFIX)) {
            throw AuthException.malformedAuthorizationHeader();
        }

        String token = authorizationHeader.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw AuthException.malformedAuthorizationHeader();
        }
        return token;
    }
}
