# 부하 테스트 (k6)

GiveMeTicket에 부하를 주는 [k6](https://k6.io) 스크립트 모음.

## 설치

```bash
brew install k6      # macOS
# 또는 https://k6.io/docs/get-started/installation/
```

## 사전 준비

서비스를 먼저 띄운다.

```bash
docker compose up -d
```

기본 대상은 `http://localhost:18080`. 다른 주소면 `-e BASE_URL=...`로 바꾼다.

모든 스크립트는 캠페인을 만들 때 userId 1을 개설자로 쓴다. 신청자는 VU마다 다른 id를
쓰기 때문에 `(campaign_id, user_id)` 유니크 제약에 걸리지 않는다.

API가 JWT를 요구하므로 [auth.js](auth.js)가 서버와 같은 시크릿으로 액세스 토큰을 직접 서명한다.
`.env`의 `JWT_SECRET_KEY`가 기본값이 아니면 넘겨줘야 한다.

```bash
k6 run -e JWT_SECRET_KEY="$(grep '^JWT_SECRET_KEY=' .env | cut -d= -f2-)" load-test/smoke.js
```

## 스크립트

| 파일 | 목적 | 실행 |
| --- | --- | --- |
| `smoke.js` | 전체 API 흐름 1회 점검 | `k6 run load-test/smoke.js` |
| `rush.js` | 오픈 스파이크 + 오버셀 검증 | `k6 run load-test/rush.js` |
| `capacity.js` | 계단식 증가로 한계 지점 탐색 | `k6 run load-test/capacity.js` |
| `soak.js` | 지속 혼합 부하(조회·신청) | `k6 run load-test/soak.js` |
| `gc-matrix.sh` | 힙/GC 조합을 바꿔 가며 반복 측정 | `./load-test/gc-matrix.sh` |

### smoke.js — 기능 점검

무료/유료 캠페인을 각각 만들어 신청·확정·취소를 돌리고, 역할별 상세 조회(GUEST·PARTICIPANT·OWNER),
목록 조회(owned·participated), 정원 증원/감원, 삭제 거부까지 한 번에 확인한다.
배포 후 "API가 살아 있는지"를 보는 용도다.

홀드 만료는 기본 2분이라 여기서 검증하지 않는다. 확인하려면 짧게 띄운다.

```bash
APPLICATION_HOLD_DURATION=5s docker compose up -d backend
```

### rush.js — 오픈 스파이크

정원만큼만 신청이 성공(201)하고 나머지는 매진(409)이 되는지, 그 와중에 5xx가
없는지 확인한다. 종료 시 잔여 재고와 성공 수를 출력한다.

```bash
k6 run -e STOCK=100 -e RATE=600 -e DURATION=30s load-test/rush.js
```

- `STOCK` 정원, `RATE` 초당 요청 수, `DURATION` 지속 시간
- 판정: `apply_created ≤ STOCK`, 잔여 재고 `≥ 0`, `http_req_failed < 1%`

`http_req_duration` 임계값(`p(95)<1000`)은 **포화 상태에서 넘는 것이 정상**이다.
로컬 compose는 backend·mysql에 각각 1 CPU만 주기 때문에, 600 rps에서는 큐잉으로 p95가 초 단위까지
올라간다. 지연을 보려면 포화되지 않는 구간(`RATE=150` 정도)에서 재면 된다. 한계 지점 자체를
찾는 것이 목적이면 `capacity.js`를 쓴다.

### soak.js — 지속 부하 + 장애 실험

조회 → 신청 → 취소를 계속 돌린다. 실행 중에 장애를 주입하면 연쇄 전파를 볼 수 있다.

```bash
# 터미널 A: 부하
k6 run -e VUS=100 -e DURATION=3m load-test/soak.js

# 터미널 B: 장애 주입
docker compose stop redis                 # 신청 전면 실패
docker compose stop mysql                 # 신청 기록 실패 → 커넥션 풀 고갈
```

### payment-mock

결제 개념이 없어지면서 백엔드는 더 이상 이 서비스를 호출하지 않는다. 외부 의존 장애를 흉내 내는
독립 서버로만 남아 있어 기본 프로필에서는 뜨지 않는다.

```bash
docker compose --profile payment-mock up -d payment-mock
curl localhost:18081/fault
```

주입 가능한 결함은 `.env`의 `PAYMENT_*` 값으로 조절한다(`PAYMENT_DELAY_MS`,
`PAYMENT_JITTER_MS`, `PAYMENT_ERROR_RATE`, `PAYMENT_TIMEOUT_RATE`, `PAYMENT_DECLINE_RATE`,
`PAYMENT_CANCEL_ERROR_RATE`). 지웠던 결제 연동 설계는
[docs/payment-flow.md](../docs/payment-flow.md)에 기록으로 남겨 두었다.

### gc-matrix.sh — 힙/GC 비교

힙 크기와 GC 종류를 바꿔 가며 같은 부하를 반복하고, 구간별 GC 횟수·정지 시간·할당률·p99를 표로
뽑는다. 실행 방법과 해석은 [docs/gc-experiment.md](../docs/gc-experiment.md)에 정리했다.

```bash
docker compose --profile obs up -d   # 프로메테우스가 떠 있어야 한다
./load-test/gc-matrix.sh
```

## 주요 옵션

- `-e BASE_URL=http://localhost:18080` 대상 주소
- `--out json=result.json` 결과를 파일로 저장
- 요약에서 `http_req_duration`, `http_req_failed`, 커스텀 카운터(`apply_created`,
  `apply_sold_out`)를 확인한다
