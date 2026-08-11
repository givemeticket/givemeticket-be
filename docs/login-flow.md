# 로그인 플로우 (프론트 연동 가이드)

소셜 로그인은 **호출 한 번**으로 끝난다. 인가 코드를 넘기면 액세스 토큰이 나온다.
가입 여부로 흐름이 갈리지 않는다 — 처음 온 계정이면 그 자리에서 가입까지 끝난다.

지원 제공자는 **카카오, 네이버**다. 흐름은 같고 `provider` 값만 다르다.

모든 경로 앞에 `/api/v1`이 붙는다.

```
[1] 소셜 인증 페이지            프론트가 리다이렉트 → 인가 코드(code) 수신
         │
         ▼
[2] POST /api/v1/code           code → 액세스 토큰 (없는 계정이면 가입까지)
         │
         ▼
[3] 이후 모든 API               Authorization: Bearer {액세스 토큰}
```

**닉네임과 프로필 이미지는 제공자에게서 가져온다.** 프론트가 따로 입력받아 보낼 것이 없다.

> 리프레시 토큰은 아직 없다. 액세스 토큰(7일)이 만료되면(`EXPIRED_TOKEN`) [1]번부터 다시 시작해야 한다.

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

## [2] `POST /api/v1/code` — 로그인

### 요청

```json
{
  "code": "인가 코드",
  "provider": "kakao",
  "redirectUrl": "https://givemeticket.site/oauth/kakao",
  "state": "네이버만 필수"
}
```

| 필드 | 카카오 | 네이버 |
| --- | --- | --- |
| `code` | 필수 | 필수 |
| `provider` | `"kakao"` | `"naver"` |
| `redirectUrl` | 필수 — [1]과 같은 값 | 필수(값은 무시됨) |
| `state` | 불필요 | **필수** — [1]과 같은 값 |

### 응답 `200`

```json
{ "token": "액세스 토큰" }
```

가입 여부와 무관하게 항상 200이다. 신규 가입이었는지는 응답에 담기지 않는다.

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

## [3] 이후 API 호출

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
{ "code": "EXPIRED_TOKEN", "message": "토큰이 만료되었습니다. 다시 로그인해 주세요." }
```

| code | 상태 | 프론트 처리 |
| --- | --- | --- |
| `MISSING_TOKEN` | 401 | 로그인 화면으로 |
| `MALFORMED_AUTHORIZATION_HEADER` | 401 | `Bearer ` 접두사 확인 (버그) |
| `EXPIRED_TOKEN` | 401 | 로그인 화면으로 |
| `INVALID_TOKEN` | 401 | 로그인 화면으로 |

`message`는 그대로 노출해도 되게 쓰여 있지만, 분기는 반드시 `code`로 한다.

---

## 닉네임과 프로필 이미지

제공자가 주는 값을 그대로 쓴다.

| | 출처 |
| --- | --- |
| 카카오 | ID 토큰의 `nickname` / `picture` 클레임 |
| 네이버 | 프로필 API의 `nickname` / `profile_image` |

둘 다 **선택 동의 항목**이라 사용자가 거부하면 값이 오지 않는다. 그 경우 닉네임은
`카카오사용자2776`처럼 대체값이 자동으로 붙고, 프로필 이미지는 비워 둔다. 로그인은 정상 진행된다.

콘솔에서 동의 항목(닉네임 / 프로필 사진)을 켜 두어야 값이 내려온다.

**이미 가입한 계정의 닉네임은 다시 로그인해도 덮어쓰지 않는다.** 제공자 쪽에서 이름을 바꿔도
우리 서비스의 표시 이름은 그대로다.

---

## 프론트 체크리스트

- [ ] 닉네임 입력 화면이 필요 없다. `/code` 응답 토큰만 저장하면 로그인 완료다
- [ ] `redirectUrl`은 [1]과 [2]에서 같은 값을 쓴다
- [ ] **네이버는 `state`를 [1]에서 만들어 [2]까지 들고 간다.** 빠지면 `STATE_REQUIRED`
- [ ] `code`는 1회용이라 재시도 시 [1]부터 다시 받는다
- [ ] 액세스 토큰 만료(7일) 시 재발급 수단이 없으므로 전체 재로그인으로 처리한다

## Swagger

`/swagger-ui`에서 요청·응답 스키마를 직접 확인할 수 있다. 우측 상단 **Authorize**에 액세스 토큰을
넣으면 인증이 필요한 API도 바로 호출된다.
