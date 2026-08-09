# givemeticket-be

선착순 티켓 신청 API.

## 실행

```bash
cp .env.example .env
docker compose up -d
```

관측 스택(Prometheus / Grafana / Loki / Alloy)은 opt-in이다.

```bash
docker compose --profile obs up -d
```

포트와 결제 장애 주입 값은 `.env`에서 바꾼다.

> 이미 데이터가 있는 DB에 붙인다면 [docs/sql/2026-08-09-campaign-api.sql](docs/sql/2026-08-09-campaign-api.sql)을
> 한 번 돌려야 한다. `ddl-auto=update`는 컬럼을 추가만 하고 기존 컬럼의 타입은 바꾸지 않아서,
> 예전에 네이티브 ENUM으로 만들어진 컬럼에 새 상태값이 들어가지 못한다. 새로 만드는 DB는 그냥 뜬다.

## API

`/api/v1` 프리픽스가 붙는다. 전체 명세는 `/swagger-ui`.

| 메서드 | 경로 | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/campaigns` | 행사 생성. 응답의 `shortCode`가 공유 링크가 된다 | O |
| GET | `/campaigns/{shortCode}` | 행사 상세. `viewerRole`로 역할을 구분해서 내려준다 | 선택 |
| GET | `/campaigns?scope=owned` | 내가 만든 행사 | O |
| GET | `/campaigns?scope=participated` | 내가 참여중인 행사 (나의 티켓) | O |
| PATCH | `/campaigns/{id}` | 오픈 지연 / 정원 증원 | O (개설자) |
| DELETE | `/campaigns/{id}` | 행사 삭제 (soft delete) | O (개설자) |
| POST | `/campaigns/{id}/apply` | 신청. 자리만 잡는다 | O |
| POST | `/applications/{id}/confirm` | 결제 요청 후 확정 | O |
| POST | `/applications/{id}/cancel` | 신청 취소. 재고 즉시 반납 | O |
| GET | `/applications/{id}` | 신청 조회 | O |

신청은 두 단계다. `apply`는 재고만 잡고, 결제는 `confirm`이 한다.

- 결제가 없는 캠페인은 `apply` 한 번으로 `CONFIRMED`가 된다
- 결제가 있는 캠페인은 `apply`가 `PENDING` + `expiresAt`을 내려주고, 클라이언트가 `confirm`을 호출한다
- `expiresAt`(기본 2분)까지 `confirm`하지 않으면 자리가 자동으로 회수된다

인증은 아직 `X-User-Id` 헤더다. 카카오/네이버 OIDC로 바꿀 때
[CurrentUserIdArgumentResolver](src/main/java/kr/givemeticket/api/global/web/CurrentUserIdArgumentResolver.java)
한 곳만 교체하면 되도록 격리해 뒀다.

`viewerRole`은 `GUEST`(비로그인) / `VIEWER`(비참여) / `PARTICIPANT`(참여자) / `OWNER`(개설자)다.
프론트가 응답 필드의 null 여부로 화면을 추측하지 않도록 역할을 명시적으로 내려준다.

### 행사 안내 정보 (`detail`)

신청·재고 로직에는 관여하지 않고 화면에만 쓰인다. `detail` 전체가 선택이고 그 안의 필드도 전부 선택이다.
등록된 게 없으면 응답에서 `detail`이 통째로 `null`이다.

| 필드 | 설명 |
| --- | --- |
| `content` | 본문 (최대 5000자) |
| `eventAt` / `eventEndAt` | 행사 시작·종료 일시. 신청 오픈 시각(`openAt`)과는 별개다 |
| `location` / `address` | 장소명과 상세 주소 |
| `imageUrl` | 포스터·썸네일 |
| `contact` | 주최자 문의처 |
| `price` | 참가비(원). 화면 안내용이고 결제 금액과 연동되지는 않는다 |

캠페인 테이블에 `detail_*` 컬럼으로 함께 저장한다. 캠페인 없이 존재할 수 없고 항상 같이 읽히기 때문에
테이블을 나누면 조인만 늘어난다.

`PATCH`로 `detail`을 보내면 **통째로 교체된다.** 일부만 바꾸려면 전체를 다시 보내야 하고,
빈 객체(`{}`)를 보내면 안내 정보가 지워진다. 필드별로 병합하면 "값을 지운다"를 표현할 방법이 없어서다.

목록 조회에는 카드에 필요한 `eventAt` / `location` / `imageUrl`만 펼쳐진다. 본문은 상세 조회에서만 내려간다.

매진(`FULL`)은 저장하지 않고 잔여 재고가 0인지로 조회 시점에 파생시킨다. 그래서 정원을 늘리면
별도의 재오픈 처리 없이 곧바로 다시 신청할 수 있게 된다.

신청·결제의 상태 전이와 실패 분기는 [docs/payment-flow.md](docs/payment-flow.md)에 정리돼 있다.

## 로깅

애플리케이션 로그는 전부 JSON 한 줄로 나간다. 프로파일별 출력 대상은 이렇다.

| 프로파일 | 출력 |
|---|---|
| (없음) / `local` / `test` | 콘솔 |
| `dev` (로컬 docker compose) | 콘솔 + `/logs/app/app.log` |
| `prod` | `/logs/app/app.log` (10MB 롤링, 30일 보관) |

`LogFilter`가 모든 요청에 `request_id`, `request_uri`, `client_ip`, `user_id`를 MDC로 심어 두기
때문에 모든 로그 라인에 자동으로 붙는다. Grafana에서 로그 한 줄의 `request_id`를 누르면 그 요청이
남긴 전체 로그로 이동한다.

`logType` 필드로 종류를 구분한다 — `REQUEST`, `RESPONSE`, `BUSINESS`, `INFO`,
`CLIENT_ERROR`, `EXTERNAL_ERROR`, `SERVER_ERROR`.

요청 body는 `SensitiveDataMasker`를 거쳐 기록된다. `password`, `card`, `email` 같은 키의 값은
`****`로 치환되고, JSON이 아닌 body(multipart 등)는 아예 남기지 않는다. 마스킹 대상과 제외 경로는
`app.logging.*`로 바꾼다 (기본값은 `LogProperties` 참고).

비즈니스 액션은 컨트롤러 메서드에 `@BusinessLogging("캠페인 신청")`을 붙이면 성공/실패가 함께 기록된다.

### 로그 수집

`backend`가 파일에 쓴 JSON을 `alloy`가 tail해서 `loki`로 push하고, Grafana의 Loki 데이터소스로 조회한다.
`alloy`는 backend와 같은 `backend-logs` 볼륨을 read-only로 마운트한다.

```
backend → /logs/app/*.log → alloy → loki → grafana
```

라벨은 `service`, `env`, `level`, `log_type`만 쓴다. `request_id`나 `uri`처럼 값이 많은 필드는
라벨로 올리지 않고 `| json` 필터로 검색한다.

```logql
{service="givemeticket-api", log_type="SERVER_ERROR"}
{service="givemeticket-api"} | json | request_id="a1b2c3d4"
```

Loki는 단일 서버 filesystem 스토리지에 30일치를 보관한다. GCS로 옮기려면
[observability/loki/loki-config.yml](observability/loki/loki-config.yml) 상단 주석을 참고한다.

배포는 [docs/deploy.md](docs/deploy.md) 참고.
