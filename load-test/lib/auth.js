import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

/**
 * 부하 스크립트용 액세스 토큰 생성.
 *
 * 서버와 같은 비밀키로 HS256 서명을 만든다. 로그인 API를 거치지 않으므로
 * 소셜 제공자 없이도 원하는 수만큼 사용자를 만들 수 있다.
 *
 * 서버의 JwtProvider 와 맞춰야 하는 것은 세 가지다.
 *   - tokenType 클레임이 "ACCESS" 여야 한다 (PROVIDER 토큰은 일반 API 에서 거부된다)
 *   - subject 가 userId 문자열이어야 한다
 *   - 비밀키가 같아야 한다 (JWT_SECRET_KEY)
 */
const SECRET = __ENV.JWT_SECRET_KEY || 'local-development-only-secret-key-32bytes';
const TTL_SECONDS = 3600;

export function accessToken(userId) {
  const now = Math.floor(Date.now() / 1000);

  const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
  const payload = encoding.b64encode(
    JSON.stringify({
      tokenType: 'ACCESS',
      sub: String(userId),
      iat: now,
      exp: now + TTL_SECONDS,
    }),
    'rawurl'
  );

  const signingInput = `${header}.${payload}`;
  const signature = crypto.hmac('sha256', SECRET, signingInput, 'base64rawurl');

  return `${signingInput}.${signature}`;
}

export function authHeaders(userId, extra) {
  return Object.assign({ Authorization: `Bearer ${accessToken(userId)}` }, extra || {});
}

export function jsonAuthHeaders(userId) {
  return authHeaders(userId, { 'Content-Type': 'application/json' });
}
