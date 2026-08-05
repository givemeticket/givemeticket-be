# givemeticket-be

선착순 티켓 신청 API. 오픈 순간 트래픽이 몰려도 오버셀이 나지 않는 것을 목표로 한다.

## 스택

Java 21 · Spring Boot 3.3.5 · MySQL 8 · Redis 7 · Prometheus · Grafana

## 로컬 실행

```bash
docker compose up -d
```

| 서비스 | 주소 |
| --- | --- |
| backend | http://localhost:18080 |
| payment-mock | http://localhost:18081 |
| mysql | localhost:3307 |
| redis | localhost:6379 |

관측 스택은 opt-in이다.

```bash
docker compose --profile obs up -d
```

| 서비스 | 주소 | 계정 |
| --- | --- | --- |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3001 | 익명 Admin |

## API

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/campaigns` | 캠페인 등록 |
| GET | `/campaigns/{campaignId}` | 캠페인 조회 (잔여 재고 포함) |
| POST | `/campaigns/{campaignId}/apply` | 선착순 신청 |
| GET | `/applications/{applicationId}` | 신청 내역 조회 |
| POST | `/applications/{applicationId}/confirm` | 결제 확정 |

인증은 범위 밖이다. `X-User-Id` 헤더 값을 유저 식별자로 쓴다.

```bash
curl -X POST localhost:18080/campaigns -H 'Content-Type: application/json' \
  -d '{"title":"티켓","totalStock":100,"openAt":"2026-01-01T00:00:00"}'

curl -X POST localhost:18080/campaigns/1/apply -H 'X-User-Id: 1'
```

`openAt`은 UTC 기준이며 미래 시각이어야 한다. 스케줄러가 1초마다 폴링해 `OPEN`으로 전환한다.

## 결제 장애 주입

payment-mock은 환경변수로 결제 게이트웨이 장애를 재현한다. 현재 값은 `GET /fault`로 확인한다.

| 변수 | 효과 |
| --- | --- |
| `PAYMENT_DELAY_MS` / `PAYMENT_JITTER_MS` | 응답 지연과 지터 |
| `PAYMENT_ERROR_RATE` | 5xx 반환 비율 |
| `PAYMENT_TIMEOUT_RATE` | `PAYMENT_TIMEOUT_MS`만큼 지연시켜 클라이언트 read timeout 유발 |
| `PAYMENT_DECLINE_RATE` | `DECLINED` 반환 비율 |

## 부하 테스트

`load-test/README.md` 참고.

```bash
k6 run load-test/smoke.js
k6 run -e STOCK=100 -e RATE=800 -e DURATION=30s load-test/rush.js
```

## 배포

`main`에 머지되면 GitHub Actions가 변경된 서비스의 이미지를 Docker Hub로 push하고, self-hosted 러너가 서버에서 `deploy.sh`를 실행한다.

### 최초 설정

**1. private 설정 리포** — `docker/config.example/README.md` 참고.

**2. 시크릿·변수 등록**

| 종류 | 이름 | 값 |
| --- | --- | --- |
| Secret | `DOCKER_USERNAME` / `DOCKER_PASSWORD` | Docker Hub 계정 |
| Secret | `SUBMODULE_KEY` | 설정 리포 Contents:Read 권한 PAT |
| Variable | `CONFIG_REPO` | 설정 리포 (기본 `givemeticket/givemeticket-submodule`) |
| Variable | `DEPLOY_DIR` | 서버 배포 경로 (기본 `~/givemeticket-prod`) |

**3. self-hosted 러너 등록** — 배포 서버에서 실행한다. 토큰은 Settings → Actions → Runners → New self-hosted runner에서 발급한다.

```bash
mkdir ~/actions-runner && cd ~/actions-runner
curl -o actions-runner.tar.gz -L https://github.com/actions/runner/releases/download/v2.328.0/actions-runner-linux-x64-2.328.0.tar.gz
tar xzf actions-runner.tar.gz
./config.sh --url https://github.com/givemeticket/givemeticket-be --token <TOKEN> --labels prod
sudo ./svc.sh install && sudo ./svc.sh start
```

라벨 `prod`가 있어야 CD 잡이 이 러너를 잡는다. 러너 계정이 `docker` 그룹에 속해야 한다.

### 프로덕션 구성

| 서비스 | 노출 |
| --- | --- |
| backend | `:80` (공개 진입점) |
| Grafana | `:3001` (관리자 계정 필요, 익명 접근 차단) |
| Prometheus · mysql · redis · payment-mock | 컨테이너 네트워크 내부 |
