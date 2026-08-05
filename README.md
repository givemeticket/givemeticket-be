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
