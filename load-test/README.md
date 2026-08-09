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

모든 스크립트는 캠페인을 만들 때 `X-User-Id: 1`을 개설자로 쓴다. 신청자는 VU마다 다른 id를
쓰기 때문에 `(campaign_id, user_id)` 유니크 제약에 걸리지 않는다.

## 스크립트

| 파일 | 목적 | 실행 |
| --- | --- | --- |
| `smoke.js` | 전체 API 흐름 1회 점검 | `k6 run load-test/smoke.js` |
| `rush.js` | 오픈 스파이크 + 오버셀 검증 | `k6 run load-test/rush.js` |
| `capacity.js` | 계단식 증가로 한계 지점 탐색 | `k6 run load-test/capacity.js` |
| `soak.js` | 지속 혼합 부하(조회·신청) | `k6 run load-test/soak.js` |

### smoke.js — 기능 점검

무료/유료 캠페인을 각각 만들어 신청까지 돌리고, 역할별 상세 조회(GUEST·PARTICIPANT·OWNER),
목록 조회(owned·participated), 정원 증원/감원, 삭제 거부까지 한 번에 확인한다.
배포 후 "API가 살아 있는지"를 보는 용도다.

### rush.js — 오픈 스파이크

정원만큼만 신청이 성공(201)하고 나머지는 매진(409)이 되는지, 그 와중에 5xx가
없는지 확인한다. 종료 시 잔여 재고와 성공 수를 출력한다.

```bash
k6 run -e STOCK=100 -e RATE=600 -e DURATION=30s load-test/rush.js
```

- `STOCK` 정원, `RATE` 초당 요청 수, `DURATION` 지속 시간
- `REQUIRES_PAYMENT=true`로 주면 결제 경로까지 포함해서 돌린다.
  기본값은 `false` — 재고 경합만 남겨서 오버셀 여부를 깨끗하게 보기 위해서다
- 판정: `apply_created + apply_unknown ≤ STOCK`, 잔여 재고 `≥ 0`, `http_req_failed < 1%`

`http_req_duration` 임계값(`p(95)<1000`)은 **포화 상태에서 넘는 것이 정상**이다.
로컬 compose는 backend·mysql에 각각 1 CPU만 주기 때문에, 600 rps에서는 큐잉으로 p95가 초 단위까지
올라간다. 지연을 보려면 포화되지 않는 구간(`RATE=150` 정도)에서 재면 된다. 한계 지점 자체를
찾는 것이 목적이면 `capacity.js`를 쓴다.

### soak.js — 지속 부하 + 장애 실험

조회 → 신청을 계속 돌린다. 신청 API가 재고 차감·결제·확정을 한 번에 처리하므로
결제 경로까지 계속 두드린다. 실행 중에 장애를 주입하면 연쇄 전파를 볼 수 있다.

```bash
# 터미널 A: 부하
k6 run -e VUS=100 -e DURATION=3m load-test/soak.js

# 터미널 B: 장애 주입
docker compose stop payment-mock          # 게이트웨이 다운 → 신청이 502
docker compose stop redis                 # 신청 전면 실패
docker compose stop mysql                 # 신청 기록 실패 → 커넥션 풀 고갈
```

### 결제 장애 주입

`.env`의 값을 바꾸고 `docker compose up -d payment-mock`으로 반영한다.
현재 값은 `curl localhost:18081/fault`로 확인한다.

| 변수 | 효과 | 기대 응답 |
| --- | --- | --- |
| `PAYMENT_DECLINE_RATE=1.0` | 카드 거절 | `409 PAYMENT_DECLINED`, 재고 복원 |
| `PAYMENT_ERROR_RATE=1.0` | 게이트웨이 5xx | `502 PAYMENT_GATEWAY_ERROR`, 재고 복원 |
| `PAYMENT_TIMEOUT_RATE=1.0` | 응답 지연(read timeout) | `202` + `UNKNOWN`, **재고 유지** |
| `PAYMENT_DELAY_MS` / `PAYMENT_JITTER_MS` | 지연 주입 | 지연만 증가 |

`UNKNOWN`은 실패가 아니라 "모름"이라 재고를 돌려주지 않는다. 이유와 이후 정산 설계는
[docs/payment-flow.md](../docs/payment-flow.md) 참고. 정산 배치는 아직 미구현이라
타임아웃을 주입한 뒤에는 `UNKNOWN` 신청이 재고를 잡은 채로 남는다.

## 주요 옵션

- `-e BASE_URL=http://localhost:18080` 대상 주소
- `--out json=result.json` 결과를 파일로 저장
- 요약에서 `http_req_duration`, `http_req_failed`, 커스텀 카운터(`apply_created`,
  `apply_sold_out`, `apply_unknown`)를 확인한다
