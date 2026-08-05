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

## 스크립트

| 파일 | 목적 | 실행 |
| --- | --- | --- |
| `smoke.js` | 배포/스크립트 정상 확인(1회) | `k6 run load-test/smoke.js` |
| `rush.js` | 오픈 스파이크 + 오버셀 검증 | `k6 run load-test/rush.js` |
| `soak.js` | 지속 혼합 부하(조회·신청·확정) | `k6 run load-test/soak.js` |

### rush.js — 오픈 스파이크

정원만큼만 신청이 성공(201)하고 나머지는 매진(409)이 되는지, 그 와중에 5xx가
없는지 확인한다. 종료 시 잔여 재고와 성공 수를 출력한다.

```bash
k6 run -e STOCK=100 -e RATE=800 -e DURATION=30s load-test/rush.js
```

- `STOCK` 정원, `RATE` 초당 요청 수, `DURATION` 지속 시간
- 판정: `apply_created ≤ STOCK`, 잔여 재고 `≥ 0`, `http_req_failed < 1%`

### soak.js — 지속 부하 + 장애 실험

조회 → 신청 → 확정을 계속 돌린다. 실행 중에 장애를 주입하면 연쇄 전파를 볼 수 있다.

```bash
# 터미널 A: 부하
k6 run -e VUS=100 -e DURATION=3m load-test/soak.js

# 터미널 B: 결제 지연/에러 주입 → confirm이 502/지연
docker compose stop payment-mock          # 게이트웨이 다운 → 502
# 또는 Redis/MySQL을 죽여 연쇄 관찰
docker compose stop redis                 # 신청 전면 실패
docker compose stop mysql                 # 신청 기록 실패 → 커넥션 풀 고갈
```

## 주요 옵션

- `-e BASE_URL=http://localhost:18080` 대상 주소
- `--out json=result.json` 결과를 파일로 저장
- k6 실행 중 `http_req_duration`, `http_req_failed`, 커스텀 카운터(`apply_created`, `apply_sold_out`)를 요약에서 확인
