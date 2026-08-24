# 신청·결제 플로우 기획

재고는 확보했는데 결제가 실패하거나, 결제 결과를 끝내 모르는 경우를 어떻게 다룰지 정한다.

> **⚠️ 폐기됨 (2026-08-24)** — 기획에서 결제 단계를 뺐다. 이 문서에 적힌 것 중
> 지금 코드에 남아 있는 것은 없다.
>
> - `POST /applications/{id}/confirm`, `PENDING` / `UNKNOWN` / `MANUAL_REVIEW` 상태,
>   홀드 만료 sweeper, `paymentKey`·`transactionId`·`expiresAt`, `Campaign.requiresPayment`,
>   `refundStatus` 가 모두 사라졌다
> - 신청은 `POST /campaigns/{id}/apply` 한 번으로 재고를 잡고 그대로 `CONFIRMED` 가 된다.
>   취소는 재고만 되돌리고 끝난다
> - `payment-mock` 모듈은 남아 있지만 백엔드가 호출하지 않는다. 외부 의존 장애를 흉내 내는
>   독립 서버로만 쓰며, compose 에서는 `--profile payment-mock` 으로만 뜬다
> - 기존 DB 정리는 `docs/sql/2026-08-24-remove-payment.sql`
>
> 아래 내용은 결제를 다시 붙일 때 되짚어볼 설계 기록으로만 남겨둔다.
> 특히 5절(실패 분기 매트릭스)과 7절(멱등성)은 그때 그대로 다시 필요해진다.

## 1. 지금 코드의 구멍 (착수 전 진단)

| # | 문제 | 위치 |
|---|---|---|
| 1 | 결제 HTTP 호출이 `@Transactional` 안에 있다. read timeout 3초 동안 DB 커넥션을 잡고 있어서, 결제 지연이 곧 커넥션 풀 고갈이 된다 | `ApplicationService.confirm` |
| 2 | 결제 타임아웃 시 `PaymentException` → 트랜잭션 롤백 → 신청은 `PENDING`, 재고는 차감된 채로 영구히 남는다. **PG에서 실제로 승인됐을 수도 있는데 아무도 확인하지 않는다** | `ApplicationService.confirm`, `HttpPaymentClient.charge` |
| 3 | `PENDING`에 만료가 없다. 결제 화면 열고 이탈하면 그 재고는 영원히 잠긴다 | `Application` |
| 4 | 멱등키가 없다. confirm 두 번 호출 = 두 번 결제 | `PaymentClient.charge` |
| 5 | 재고 복원이 멱등하지 않다. `increase()`가 두 번 돌면 재고가 `totalStock`을 넘는다 | `RedisStockRepository.increase` |
| 6 | `(campaign_id, user_id)`에 unique 제약이 없다. 한 사람이 무한히 신청 가능 | `Application` |
| 7 | `requiresPayment` 개념 자체가 없다 | `Campaign` |
| 8 | 실패 사유를 구분하지 못한다. `SOLD_OUT`과 `PAYMENT_FAILED`가 같은 `FAILED` | `ApplicationStatus` |

## 2. 상태 모델

`ApplicationStatus`에 `failureReason`을 분리한다. 상태는 전이 규칙을, 사유는 화면 문구를 결정한다.

```
                      ┌──────────────────────────────► CONFIRMED
                      │  (requiresPayment=false, 재고 확보 즉시)
   apply
     │   재고 차감 실패 ──────────────────────────────► FAILED(SOLD_OUT)  ※ 행 저장 안 함, 예외 응답
     │
     └─► PENDING ──┬─ 결제 승인 ─────────────────────► CONFIRMED
      (expiresAt)  │
                   ├─ 결제 거절 ─────────────────────► FAILED(PAYMENT_DECLINED)   + 재고 복원
                   ├─ 결제 5xx/연결실패 ──────────────► FAILED(PAYMENT_ERROR)      + 재고 복원
                   ├─ 결제 타임아웃/응답불명 ──────────► UNKNOWN                    ★ 재고 복원 안 함
                   └─ expiresAt 경과 (sweeper) ──────► FAILED(EXPIRED)            + 재고 복원

   UNKNOWN ──┬─ 정산 배치: PG 조회 결과 승인 ─────────► CONFIRMED
             ├─ 정산 배치: PG 조회 결과 미승인 ────────► FAILED(PAYMENT_ERROR)     + 재고 복원
             └─ 정산 재시도 한도 초과 ────────────────► MANUAL_REVIEW  (알림 발송, 재고 유지)

   CONFIRMED ─ 사용자 취소 ─────────────────────────► CANCELLED + 재고 복원 (+ 결제분 취소 요청)
```

핵심은 **`UNKNOWN`을 별도 상태로 둔다**는 것이다. 타임아웃은 "실패"가 아니라 "모름"이다.
실패로 단정하고 재고를 복원하면, PG에서 실제로 승인된 경우 돈은 빠져나갔는데 티켓은 남에게 팔린다.
모를 때는 재고를 잡아둔 채로 PG에 다시 물어보는 게 유일하게 안전한 선택이다.

```java
public enum ApplicationStatus {
    PENDING, CONFIRMED, FAILED, CANCELLED, UNKNOWN, MANUAL_REVIEW
}

public enum FailureReason {
    SOLD_OUT, PAYMENT_DECLINED, PAYMENT_ERROR, EXPIRED
}
```

`Application`에 추가할 컬럼: `expires_at`, `failure_reason`, `payment_key`(멱등키), `reconcile_attempts`.

## 3. 재고 홀드

재고는 Redis 카운터 하나로 유지하고(현행 유지), 홀드의 수명은 DB의 `expires_at`이 관리한다.
Redis TTL이나 키스페이스 알림을 쓰지 않는 이유는, 만료 시 재고 복원과 상태 전이를 **원자적으로**
묶어야 하는데 그 원자성의 기준점이 DB 행이기 때문이다.

**홀드 시간은 설정값(`application.hold-duration`)이고 기본 2분이다.**

`apply`가 자리를 잡고 `confirm`이 결제하는 구조라, `PENDING` 수명은 사용자가 결제 화면에
머무는 시간이다. 실 PG로 바꿔도 이 값만 조정하면 되고 상태머신은 그대로다.

## 4. 정상 플로우

`apply`는 자리를 잡는 것까지만 한다. 결제는 `confirm`이 맡는다.
apply 하나에 재고·결제·확정을 다 넣으면 실패 분기가 전부 그 한 곳으로 몰린다.

### 4-1. `requiresPayment = false`

```
POST /api/v1/campaigns/{id}/apply
  1. 오픈 여부 확인                   → 아니면 409 CAMPAIGN_NOT_OPEN
  2. Redis DECR                      → 실패면 409 SOLD_OUT
  3. [tx] Application(CONFIRMED) 저장 → 실패면 Redis INCR 보상
  4. 201 { status: CONFIRMED }
```

`PENDING`을 거치지 않으니 sweeper 대상도, confirm 호출도 필요 없다.

### 4-2. `requiresPayment = true`

```
POST /api/v1/campaigns/{id}/apply
  1. 오픈 여부 확인
  2. Redis DECR                      → 실패면 409 SOLD_OUT
  3. [tx] 기존 신청 조회
       ├─ 살아있는 신청 있음  → 409 ALREADY_APPLIED (재고 보상)
       └─ 없음/종결됨        → Application(PENDING, expiresAt=now+hold,
                                paymentKey=UUID) 저장
  4. 201 { status: PENDING, expiresAt }

POST /api/v1/applications/{id}/confirm
  1. 소유자·PENDING 확인              → 아니면 409
  2. 만료됐으면 그 자리에서 회수       → 409 APPLICATION_EXPIRED
  ── 트랜잭션 밖 ──
  3. paymentClient.charge(paymentKey, ...)
  ── 결과에 따라 ──
  4. [tx] 상태 전이 (+ 필요 시 재고 복원)
  5. 200 / 202 / 409 / 502
```

중복 확인이 재고 차감보다 **뒤에** 있다. 오픈 직후에는 요청 대부분이 매진으로 떨어지는데,
중복 확인이 앞에 있으면 그 요청들이 전부 DB 커넥션을 한 번씩 잡고 나간다.
이 순서면 매진 경로는 Redis 두 번으로 끝난다. 중복이면 방금 잡은 자리를 되돌린다.

## 5. 실패 분기 매트릭스

| 상황 | PG 실제 결과 | 전이 | 재고 | 사용자 응답 |
|---|---|---|---|---|
| 재고 없음 | 호출 안 함 | 저장 안 함 | 변화 없음 | `409 SOLD_OUT` |
| 카드 거절 (`approved=false`) | 미승인 확정 | `FAILED(PAYMENT_DECLINED)` | 복원 | `409 PAYMENT_DECLINED` |
| PG 5xx | 미승인 확정 | `FAILED(PAYMENT_ERROR)` | 복원 | `502 PAYMENT_ERROR` |
| 연결 실패 (요청 전송 실패) | 미승인 확정 | `FAILED(PAYMENT_ERROR)` | 복원 | `502 PAYMENT_ERROR` |
| **read timeout** | **불명** | `UNKNOWN` | **유지** | `202` + `UNKNOWN` |
| 응답 파싱 실패 / null | **불명** | `UNKNOWN` | **유지** | `202` + `UNKNOWN` |
| confirm 중 서버 크래시 | 불명 | `PENDING` 잔류 → 만료되면 sweeper가 회수 | 만료 시 복원 | (재조회 시 확인) |
| 홀드 만료 (confirm 안 함) | 호출 안 함 | `FAILED(EXPIRED)` | 복원 | `409 APPLICATION_EXPIRED` |

"연결 실패"와 "read timeout"을 반드시 구분해야 한다.
전자는 요청이 PG에 닿지 않은 게 확실하므로 즉시 실패 처리해도 안전하다.
후자는 요청은 갔고 응답만 못 받은 것이므로 절대 실패로 단정하면 안 된다.

```java
catch (ResourceAccessException e) {
    if (e.getCause() instanceof ConnectException) throw PaymentException.notSent();   // 확정 실패
    throw PaymentException.unknown();                                                  // 불명
}
```

`202`를 받은 클라이언트는 "결제 확인 중입니다" 모달을 띄우고
`GET /api/v1/applications/{id}`를 2초 간격으로 폴링한다. 최대 30초까지 폴링하고,
그래도 `UNKNOWN`이면 "확인되면 알려드립니다"로 마무리한다.

## 6. 미결(UNKNOWN) 정산 배치

`UNKNOWN`을 해소하는 유일한 방법은 PG에 멱등키로 다시 물어보는 것이다.

```
@Scheduled(fixedDelay = 5s)
reconcile():
  UNKNOWN 이면서 reconcile_attempts < 5 인 신청을 100건씩 조회
  for each:
    GET /payments/{paymentKey}
      ├─ APPROVED  → [tx] CONFIRMED
      ├─ DECLINED  → [tx] FAILED(PAYMENT_DECLINED) + 재고 복원
      ├─ NOT_FOUND → 결제 요청이 PG에 도달하지 않았다
      │              → [tx] FAILED(PAYMENT_ERROR) + 재고 복원
      └─ 조회 자체 실패 → attempts++ , 다음 주기로 (지수 백오프)
  attempts >= 5 → MANUAL_REVIEW 로 전이 + ERROR 로그 + 알림
```

`NOT_FOUND`를 실패로 간주해도 되는 이유는 멱등키가 PG 쪽에 먼저 기록되기 때문이다.
키가 없다 = 요청이 처리되지 않았다.

`MANUAL_REVIEW`는 재고를 계속 잡아둔다. 사람이 판단할 때까지 그 자리는 비워두는 게
"돈 받고 자리 없음"보다 낫다. 대신 운영자용 조회 API(`GET /api/v1/admin/applications?status=MANUAL_REVIEW`)를
같이 만든다.

## 7. 멱등성

### 7-1. 신청 멱등

```sql
ALTER TABLE application
  ADD CONSTRAINT uk_application_campaign_user UNIQUE (campaign_id, user_id);
```

종결 상태(`FAILED`, `CANCELLED`)에서 재신청을 허용하려면 unique로는 안 된다.
대안 두 가지 중 **(b)를 택한다.**

- (a) `(campaign_id, user_id, status)` 복합 unique — MySQL은 NULL 중복을 허용하므로
  "활성 신청만 유일"을 표현하려면 `active_marker` 같은 생성 컬럼이 필요하다. 지저분하다.
- (b) `(campaign_id, user_id)` unique + **재신청 시 새 행을 만들지 않고 기존 행을 재사용**한다.
  종결 상태의 행을 `PENDING`으로 되돌리고 `paymentKey`를 새로 발급한다. 이력이 필요해지면
  그때 `application_history` 테이블을 따로 판다.

`DataIntegrityViolationException`은 `409 ALREADY_APPLIED`로 매핑한다.
동시에 두 요청이 들어와도 DB가 한 건만 통과시킨다.

### 7-2. 결제 멱등

`paymentKey`(신청당 UUID 1개)를 PG에 함께 보낸다. 같은 키로 두 번 요청하면
PG는 결제를 새로 만들지 않고 첫 결과를 그대로 돌려준다. 재시도와 정산 조회가 모두 이 키를 쓴다.

### 7-3. 재고 복원 exactly-once

상태 전이가 성공한 요청만 복원한다. 조건부 UPDATE의 반환 행 수를 락 대신 쓴다.

```java
@Transactional
public boolean failAndRestore(Long id, FailureReason reason) {
    int updated = repository.updateStatusIf(id, PENDING, FAILED, reason);  // WHERE status = 'PENDING'
    if (updated == 0) {
        return false;   // sweeper 등 다른 주체가 이미 전이시켰다 → 복원하지 않는다
    }
    registerAfterCommit(() -> stockRepository.increase(campaignId));
    return true;
}
```

`afterCommit`으로 미루는 이유는, 트랜잭션이 롤백되면 Redis 복원도 없어야 하기 때문이다.
다만 커밋 직후 프로세스가 죽으면 재고 1이 영구히 증발한다 — dual write의 본질적 한계라
코드로는 못 막는다. 8절의 보정 배치로 수습한다.

`increase`도 상한을 걸어 카운터가 `totalStock`을 넘지 못하게 한다.

```lua
local cur = tonumber(redis.call('GET', KEYS[1]))
if cur == nil then return -1 end
if cur >= tonumber(ARGV[1]) then return cur end   -- 이미 꽉 참, no-op
return redis.call('INCR', KEYS[1])
```

## 8. 재고 정합성 보정 배치

Redis 카운터는 파생값이고, 진실은 DB다.

```
@Scheduled(fixedDelay = 60s)   // OPEN 상태 캠페인만
expected = totalStock - count(status IN (PENDING(미만료), CONFIRMED, UNKNOWN, MANUAL_REVIEW))
actual   = GET campaign:stock:{id}
불일치면 WARN 로그 + Lua CAS 로 보정
```

Redis 재시작·유실 시 재고를 되살리는 경로이기도 하다.
불일치 건수는 메트릭(`stock_drift_total`)으로 올려서 Grafana에서 본다 — 0이 아니면 어딘가 버그다.

## 9. 만료 sweeper

```
@Scheduled(fixedDelay = 10s)
  PENDING 이고 expires_at < now 인 신청 조회 (idx_application_status_expires)
    ├─ 결제 시도 흔적 없음(paymentRequestedAt == null) → FAILED(EXPIRED) + 재고 복원
    └─ 결제 시도했으나 결과 미기록                      → UNKNOWN (정산 배치로 넘김)
```

`paymentRequestedAt`을 PG 호출 직전에 별도 트랜잭션으로 기록해야 이 구분이 가능하다.
쓰기가 한 번 늘지만, 크래시 복구의 정확도를 위해 감수한다.

## 10. 취소

```
POST /api/v1/applications/{id}/cancel
  1. 소유자 확인                          → 아니면 403
  2. UNKNOWN / MANUAL_REVIEW 인가          → 409 APPLICATION_SETTLEMENT_PENDING
  3. status == CONFIRMED 인가              → 아니면 409 APPLICATION_NOT_CANCELABLE
  4. [tx] CONFIRMED → CANCELLED (조건부 UPDATE, 0행이면 동시 취소이므로 409)
  5. 재고 복원
  6. requiresPayment면 결제 취소 요청 (실패해도 취소는 유지)
```

**결제가 없던 신청은 4~5로 끝난다.** 외부 호출이 없으니 지연도 실패 지점도 없다.
응답의 `refundStatus`는 `NOT_REQUIRED`다.

결제가 있었으면 자리를 먼저 돌려주고(5) 환불을 요청한다(6). 순서가 중요하다 — 환불 응답을
기다리느라 자리를 늦게 풀면 그동안 아무도 그 자리를 못 산다.

`UNKNOWN`을 취소 대상에서 빼는 이유는, 환불 대상인지 아닌지가 아직 정해지지 않았기 때문이다.
취소부터 받으면 자리는 남에게 넘어가고 돈의 행방만 남는다. 정산이 끝나 `CONFIRMED`가 되면
그때 취소할 수 있다.

결제 취소가 실패해도 신청 취소는 되돌리지 않는다. 사용자 입장의 취소는 이미 끝났고,
환불은 뒤에서 재시도하면 되는 문제다. 응답에 `PENDING_RETRY`로 표시하고 ERROR 로그를 남긴다.

> **자동 재시도 큐는 아직 없다.** `PENDING_RETRY`가 나오면 현재로선 로그가 유일한 추적 수단이다.
> 14절의 메트릭과 함께 붙여야 한다.

## 11. 캠페인 변경과의 상호작용

| 관리자 동작 | 진행 중인 신청 처리 |
|---|---|
| 정원 증원 (`totalStock` +N) | Redis 카운터 `INCRBY N`, 상한값도 같이 갱신. 재고 0이었으면 자동으로 다시 신청 가능 |
| 오픈 시각 지연 | 아직 오픈 전이므로 진행 중 신청 없음. 이미 오픈된 캠페인은 지연 불가 (409) |
| 캠페인 삭제 | soft delete. 유효한 신청(`PENDING`·`CONFIRMED`·`UNKNOWN`·`MANUAL_REVIEW`)이 하나라도 있으면 거부(409), 없으면 `DELETED` 전이 + Redis 키 제거 |

`CONFIRMED`만 막지 않고 `PENDING`·`UNKNOWN`까지 막는다. 결제가 진행 중이거나 결과를 모르는 건이
남은 채로 캠페인을 지우면, 그 돈을 어떻게 처리할지 판단할 근거가 사라진다.

**`FULL`은 별도 상태로 저장하지 않고 조회 시점에 `remaining == 0`으로 파생시킨다.**
저장하면 재고 변화마다 상태를 동기화해야 하고, 그 동기화가 어긋나는 순간
"재고는 있는데 FULL로 보이는" 버그가 생긴다. 파생값이면 그 클래스의 버그가 아예 없다.

## 12. 응답 코드

| 코드 | HTTP | 의미 |
|---|---|---|
| `SOLD_OUT` | 409 | 재고 없음 |
| `CAMPAIGN_NOT_OPEN` | 409 | 오픈 전이거나 종료됨 |
| `ALREADY_APPLIED` | 409 | 이미 신청함 |
| `PAYMENT_DECLINED` | 409 | 결제 거절 (재시도 가능) |
| `PAYMENT_ERROR` | 502 | 결제 게이트웨이 오류 (재시도 가능) |
| `PAYMENT_PENDING` | 202 | 결제 결과 확인 중 (폴링) |
| `APPLICATION_EXPIRED` | 409 | 홀드 만료 |
| `APPLICATION_NOT_CANCELABLE` | 409 | 확정 상태가 아니라 취소 불가 |
| `APPLICATION_SETTLEMENT_PENDING` | 409 | 결제 결과 확인 중이라 취소 불가 |

## 13. payment-mock에 필요한 변경 (구현됨)

상태가 없는 mock은 멱등도 조회도 안 된다. 그러면 재시도가 그대로 이중 결제가 되므로
세 가지를 추가했다.

1. `POST /payments`가 `paymentKey`를 받고, 키별 결과를 인메모리 맵에 저장한다.
   같은 키 재요청 시 **저장된 결과를 그대로 반환**한다 (새로 주사위를 굴리지 않는다).
2. `GET /payments/{paymentKey}` — 정산 배치용. 없으면 404.
3. `POST /payments/{paymentKey}/cancel` — 취소용. 취소 실패율도 주입 가능하게.

기존 결함 주입(`PAYMENT_TIMEOUT_RATE` 등)은 그대로 두되, **타임아웃을 주입할 때도 결제는
성공시켜서 맵에 저장**해야 한다. 그래야 "PG는 승인했는데 우리는 모르는" 진짜 상황이 재현되고,
정산 배치가 제대로 동작하는지 검증할 수 있다. 지금처럼 sleep만 하면 이 시나리오가 안 만들어진다.

## 14. 관측

메트릭 (Micrometer → Prometheus):

| 이름 | 타입 | 용도 |
|---|---|---|
| `application_transition_total{from,to,reason}` | counter | 상태 전이 분포 |
| `payment_charge_seconds` | timer | 결제 지연 |
| `application_unknown_current` | gauge | 미결 건수. **0에서 벗어나면 알람** |
| `application_manual_review_total` | counter | 사람 개입 필요. 알람 |
| `stock_drift_total` | counter | 재고 불일치 보정 횟수. 알람 |
| `application_expired_total` | counter | 이탈률 지표 |

로그는 기존 `@BusinessLogging` + `logType`을 그대로 쓰고, 상태 전이마다
`applicationId`, `paymentKey`, `from`, `to`, `reason`을 구조화 필드로 남긴다.
`paymentKey`로 Loki를 검색하면 한 결제의 전 생애가 한 화면에 나오게 하는 게 목표다.

```logql
{service="givemeticket-api"} | json | payment_key="pk_..."
```

## 15. 검증 시나리오

k6나 통합 테스트로 반드시 확인할 것:

1. `PAYMENT_TIMEOUT_RATE=1.0` + mock은 승인 저장 → 전부 `UNKNOWN` → 정산 후 전부 `CONFIRMED`,
   최종 `CONFIRMED` 수 == `totalStock`
2. `PAYMENT_DECLINE_RATE=0.5` → 재고 복원이 정확히 거절 건수만큼, 최종 재고 정합
3. 동일 유저 동시 apply 100회 → `CONFIRMED` 정확히 1건
4. apply 도중 backend 강제 kill → 재기동 후 sweeper가 잔여 `PENDING` 전부 해소
5. 재고 100, 동시 요청 10,000 → `CONFIRMED` 정확히 100, 초과 판매 0
