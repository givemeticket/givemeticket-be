import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

// 소셜 로그인이 붙으면서 API 가 Authorization 헤더를 요구한다. 부하 테스트에서 카카오/네이버를
// 실제로 태울 수는 없으니, 서버와 같은 시크릿으로 액세스 토큰을 직접 서명해서 쓴다.
// 서버는 subject 를 userId 로 읽을 뿐 DB 를 다시 뒤지지 않기 때문에, 예전 X-User-Id 헤더와
// 똑같이 VU 마다 임의의 id 를 부여할 수 있다. (AccessTokenProvider / LoginUserIdArgumentResolver)
//
// 시크릿은 .env 의 JWT_SECRET_KEY 와 같아야 한다. 다르면 전 요청이 401 로 떨어진다.
//   k6 run -e JWT_SECRET_KEY="$(grep '^JWT_SECRET_KEY=' .env | cut -d= -f2-)" ...
const SECRET = __ENV.JWT_SECRET_KEY || 'local-development-only-secret-key-32bytes';

const b64url = (obj) => encoding.b64encode(JSON.stringify(obj), 'rawurl');
const HEADER = b64url({ alg: 'HS256', typ: 'JWT' });

// 같은 userId 로 매번 다시 서명하지 않는다. VU 당 한 번이면 충분하다.
const cache = {};

export function accessToken(userId) {
  const key = String(userId);
  if (cache[key]) return cache[key];

  const now = Math.floor(Date.now() / 1000);
  const payload = b64url({ tokenType: 'ACCESS', sub: key, iat: now, exp: now + 3600 });
  const signingInput = `${HEADER}.${payload}`;
  const signature = crypto.hmac('sha256', SECRET, signingInput, 'base64rawurl');

  cache[key] = `${signingInput}.${signature}`;
  return cache[key];
}

/** 인증만 필요한 요청용 헤더 */
export function authHeaders(userId) {
  return { Authorization: `Bearer ${accessToken(userId)}` };
}

/** 본문이 있는 요청용 헤더 */
export function jsonAuthHeaders(userId) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken(userId)}`,
  };
}
