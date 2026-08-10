# 로그인 플로우 (프론트 연동 가이드)

소셜 로그인은 **두 번 호출**한다. `/code`로 신원을 확인하고, 그 응답의 상태 코드를 보고 `/login` 또는 `/signup`으로 갈린다.

모든 경로 앞에 `/api/v1`이 붙는다.

지원 제공자는 **카카오, 네이버**다. 흐름은 완전히 같고 `provider` 값만 다르다.

```
[1] 소셜 인증 페이지            프론트가 리다이렉트 → 인가 코드(code) 수신
         │
         ▼
[2] POST /api/v1/code           code → 제공자 토큰 + 가입 여부
         │
    ┌────┴────┐
   200       401
    │         │
    ▼         ▼
[3] /login   /signup            제공자 토큰 → 액세스 토큰
         │
         ▼
[4] 이후 모든 API               Authorization: Bearer {액세스 토큰}
```

## 토큰 두 가지

서로 용도가 다르고 **바꿔 쓰면 401**이 난다.

| | 제공자 토큰 | 액세스 토큰 |
| --- | --- | --- |
| 주는 곳 | `/code` | `/login`, `/signup` |
| 쓰는 곳 | `/login`, `/signup` **전용** | 그 외 모든 API |
| 만료 | 10분 | 7일 |
| 의미 | "카카오 인증은 됐다" | "우리 서비스 유저다" |

둘 다 `Authorization: Bearer {token}` 헤더로 보낸다.

> 리프레시 토큰은 아직 없다. 액세스 토큰이 만료되면(`EXPIRED_TOKEN`) [1]번부터 다시 시작해야 한다.

---

## [1] 인가 코드 받기 — 프론트 담당

인증 페이지로 보내고, 리다이렉트로 돌아온 URL의 `code` 쿼리 파라미터를 꺼낸다.

**카카오**

```
https://kauth.kakao.com/oauth/authorize
  ?client_id={REST_API_KEY}
  &redirect_uri={REDIRECT_URI}
  &response_type=code
  &scope=openid
```

**네이버**

```
https://nid.naver.com/oauth2.0/authorize
  ?client_id={CLIENT_ID}
  &redirect_uri={REDIRECT_URI}
  &response_type=code
  &state={랜덤 문자열}
```

- `redirect_uri`는 **콘솔에 등록된 값과 정확히 일치**해야 한다. 다르면 [2]에서 `INVALID_AUTHORIZATION_CODE`가 난다
- 카카오는 `scope=openid`가 빠지면 백엔드가 검증할 `id_token`이 오지 않는다
- **네이버는 `state`가 필수**다. 여기서 만든 값을 [2]에도 그대로 넘겨야 한다
- `code`는 **1회용**이다. 같은 값으로 두 번 호출하면 실패한다

---

## [2] `POST /api/v1/code` — 인가 코드 검증

### 요청

```json
{
  "code": "인가 코드",
  "provider": "kakao",
  "redirectUrl": "https://givemeticket.kr/oauth/kakao",
  "state": "네이버만 필수"
}
```

| 필드 | 카카오 | 네이버 |
| --- | --- | --- |
| `code` | 필수 | 필수 |
| `provider` | `"kakao"` | `"naver"` |
| `redirectUrl` | 필수 — [1]과 같은 값 | 필수(값은 무시됨) |
| `state` | 불필요 | **필수** — [1]과 같은 값 |

`redirectUrl`은 지금 두 제공자 모두 필수 필드다. 네이버는 실제로 쓰지 않지만 아무 값이나 채워 보내야 한다.

### 응답

**상태 코드가 곧 가입 여부다. 200과 401 모두 body는 같다.**

| 상태 | 의미 | 다음 단계 |
| --- | --- | --- |
| `200` | 이미 가입된 계정 | `/login` |
| `401` | 아직 가입 전 | `/signup` |

```json
{ "token": "제공자 토큰" }
```

> ⚠️ **401을 일반 에러로 처리하면 안 된다.** 여기서의 401은 "가입만 안 됐을 뿐 카카오 인증은 성공"이라는 뜻이고, body에 정상적인 토큰이 들어 있다. 공통 에러 인터셉터가 401을 잡아 로그인 페이지로 튕기는 구조라면 이 엔드포인트는 예외 처리해야 한다.

### 실패

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수 필드 누락·공백 |
| 400 | `UNSUPPORTED_PROVIDER` | `provider`가 `kakao`/`naver`가 아님 |
| 400 | `STATE_REQUIRED` | 네이버인데 `state`를 안 보냄 |
| 400 | `INVALID_AUTHORIZATION_CODE` | 코드 만료·재사용, `redirectUrl`/`state` 불일치 |
| 401 | `INVALID_ID_TOKEN` | 카카오 응답을 신뢰할 수 없음 (카카오만) |
| 502 | `LOGIN_PROVIDER_ERROR` | 제공자 호출 실패 |
| 502 | `LOGIN_PROVIDER_PROFILE_ERROR` | 네이버 프로필 조회 실패 (네이버만) |

---

## [3-a] `POST /api/v1/login` — 로그인

`/code`가 **200**일 때 호출한다. body는 없다.

```
Authorization: Bearer {제공자 토큰}
```

### 응답 `200`

```json
{ "token": "액세스 토큰" }
```

### 실패

| 상태 | code | 상황 |
| --- | --- | --- |
| 404 | `USER_NOT_FOUND` | 가입 기록 없음 → `/signup`으로 가야 함 |
| 401 | `TOKEN_TYPE_MISMATCH` | 액세스 토큰을 잘못 보냄 |
| 401 | `EXPIRED_TOKEN` | 제공자 토큰 10분 만료 → [1]부터 다시 |

---

## [3-b] `POST /api/v1/signup` — 회원가입

`/code`가 **401**일 때 호출한다.

```
Authorization: Bearer {제공자 토큰}
```

```json
{
  "nickname": "민기",
  "profileImageUrl": "https://k.kakaocdn.net/dn/.../profile.jpg"
}
```

| 필드 | 필수 | 제한 |
| --- | --- | --- |
| `nickname` | 필수 | 최대 20자 |
| `profileImageUrl` | 선택 | 최대 500자. 소셜 프로필에서 받은 값을 그대로 넘기면 된다 |

**회원번호는 토큰에서 꺼내므로 body로 보내지 않는다.**

### 응답 `201`

```json
{ "token": "액세스 토큰" }
```

가입과 동시에 로그인된 상태가 된다. 따로 `/login`을 부를 필요 없다.

### 실패

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 닉네임 공백·20자 초과, `profileImageUrl` 500자 초과 |
| 409 | `USER_ALREADY_REGISTERED` | 이미 가입됨 → `/login`으로 가야 함 |
| 401 | `TOKEN_TYPE_MISMATCH` | 액세스 토큰을 잘못 보냄 |
| 401 | `EXPIRED_TOKEN` | 제공자 토큰 10분 만료 → [1]부터 다시 |

---

## [4] 이후 API 호출

캠페인·신청 등 모든 API에 액세스 토큰을 붙인다.

```
Authorization: Bearer {액세스 토큰}
```

- **캠페인 상세 조회(`GET /campaigns/{shortCode}`)만 토큰이 선택**이다. 비로그인으로 부르면 `viewerRole: GUEST`로 내려온다
- 단, 토큰을 **보냈는데 잘못된 경우**는 선택이든 아니든 401이다
- 그 외 API는 토큰이 없으면 401 `MISSING_TOKEN`

---

## 인증 관련 에러 코드 정리

에러 응답은 모두 아래 형태다.

```json
{ "code": "USER_NOT_FOUND", "message": "가입되지 않은 계정입니다. 회원가입을 먼저 진행해 주세요." }
```

| code | 상태 | 프론트 처리 |
| --- | --- | --- |
| `MISSING_TOKEN` | 401 | 로그인 화면으로 |
| `MALFORMED_AUTHORIZATION_HEADER` | 401 | `Bearer ` 접두사 확인 (버그) |
| `EXPIRED_TOKEN` | 401 | 로그인 화면으로 |
| `INVALID_TOKEN` | 401 | 로그인 화면으로 |
| `TOKEN_TYPE_MISMATCH` | 401 | 두 토큰을 바꿔 보냄 (버그) |
| `USER_NOT_FOUND` | 404 | `/signup`으로 |
| `USER_ALREADY_REGISTERED` | 409 | `/login`으로 |

`message`는 그대로 노출해도 되게 쓰여 있지만, 분기는 반드시 `code`로 한다.

---

## 프론트 체크리스트

- [ ] `/code`의 401은 **에러가 아니다**. 공통 인터셉터에서 제외한다
- [ ] `/code` 응답 토큰과 `/login`·`/signup` 응답 토큰을 **구분해서 저장**한다. 제공자 토큰은 가입 완료 후 버린다
- [ ] `redirectUrl`은 [1]과 [2]에서 같은 값을 쓴다
- [ ] **네이버는 `state`를 [1]에서 만들어 [2]까지 들고 간다.** 빠지면 `STATE_REQUIRED`
- [ ] `code`는 1회용이라 재시도 시 [1]부터 다시 받는다
- [ ] 액세스 토큰 만료(7일) 시 재발급 수단이 없으므로 전체 재로그인으로 처리한다

## Swagger

`/swagger-ui`에서 요청·응답 스키마를 직접 확인할 수 있다. 우측 상단 **Authorize**에 액세스 토큰을 넣으면 인증이 필요한 API도 바로 호출된다.
