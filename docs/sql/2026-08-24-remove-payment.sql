-- 결제 개념 제거에 따른 스키마 변경.
--
-- ddl-auto=update 는 컬럼과 인덱스를 "추가"만 한다. 쓰지 않게 된 것들은 그대로 남는데,
-- campaign.requires_payment 와 application.reconcile_attempts 는 NOT NULL 에 기본값이 없어서
-- 새 코드가 값을 빼고 INSERT 하는 순간 행사 생성·신청이 그대로 실패한다.
-- 이미 데이터가 있는 DB 는 이 스크립트를 한 번 돌려야 한다.
-- 새로 만드는 DB 는 엔티티 매핑대로 생성되므로 필요 없다.
--
-- 배포 순서 때문에 두 단계로 나눠져 있다. 한 번에 다 돌리면 어느 쪽이든 쓰기가 깨진다.
--   1단계를 배포 전에 돌리면 구 코드는 그대로 돌아가고, 신 코드가 떠도 INSERT 가 통과한다.
--   2단계는 신 코드가 뜬 뒤에 돌린다. 구 코드는 지워질 컬럼들을 아직 읽기 때문이다.

-- ============================================================
-- 1단계 — 배포 전. 구 코드와 신 코드가 함께 돌아갈 수 있게 만든다.
-- ============================================================

-- 1-1. 빠진 값을 DB 가 채워주게 한다. 구 코드는 지금처럼 값을 직접 넣고,
--      신 코드는 컬럼 자체를 모른 채 INSERT 해도 기본값이 들어간다.
ALTER TABLE campaign
    ALTER COLUMN `requires_payment` SET DEFAULT b'0';

ALTER TABLE application
    ALTER COLUMN `reconcile_attempts` SET DEFAULT 0;

-- 1-2. 사라진 enum 상수를 쓰는 기존 행을 옮긴다. 자리를 잡고 있던 상태
--      (PENDING/UNKNOWN/MANUAL_REVIEW)는 CONFIRMED 로 올린다. Redis 재고는 이 신청들 몫을
--      이미 빼둔 상태라, 이렇게 해야 재고와 신청 수가 어긋나지 않는다.
--      CONFIRMED/CANCELLED 는 구 코드에서도 유효한 값이라 지금 돌려도 안전하다.
UPDATE application
   SET status = 'CONFIRMED',
       failure_reason = NULL
 WHERE status IN ('PENDING', 'UNKNOWN', 'MANUAL_REVIEW');

-- 1-3. 자리를 놓친 상태(FAILED)는 CANCELLED 로 맞춘다. 둘 다 재고를 잡고 있지 않다.
UPDATE application
   SET status = 'CANCELLED'
 WHERE status = 'FAILED';

-- 1-4. 남아 있는 결제·만료 사유를 비운다. CAMPAIGN_DELETED / USER_WITHDRAWN 만 살아남는다.
UPDATE application
   SET failure_reason = NULL
 WHERE failure_reason IN ('SOLD_OUT', 'PAYMENT_DECLINED', 'PAYMENT_ERROR', 'EXPIRED');

-- ============================================================
-- 2단계 — 배포 후. 여기부터는 신 코드만 돌고 있어야 한다.
-- ============================================================

-- 2-1. 1단계와 배포 사이에 구 코드가 새로 만든 PENDING 이 있을 수 있다.
--      신 코드는 이 값을 읽지 못하므로 한 번 더 훑는다. 1-2~1-4 와 같은 문장이고 몇 번 돌려도 된다.
UPDATE application
   SET status = 'CONFIRMED',
       failure_reason = NULL
 WHERE status IN ('PENDING', 'UNKNOWN', 'MANUAL_REVIEW');

UPDATE application
   SET status = 'CANCELLED'
 WHERE status = 'FAILED';

UPDATE application
   SET failure_reason = NULL
 WHERE failure_reason IN ('SOLD_OUT', 'PAYMENT_DECLINED', 'PAYMENT_ERROR', 'EXPIRED');

-- 2-2. 결제 홀드가 없어져 만료 sweeper 도 없다. 그 조회를 위해 만든 인덱스를 지운다.
DROP INDEX idx_application_status_expires ON application;

-- 2-3. 결제 흔적 컬럼 제거.
ALTER TABLE application
    DROP COLUMN `payment_key`,
    DROP COLUMN `transaction_id`,
    DROP COLUMN `expires_at`,
    DROP COLUMN `payment_requested_at`,
    DROP COLUMN `reconcile_attempts`;

-- 2-4. 행사에는 더 이상 "결제가 필요한지"가 없다.
ALTER TABLE campaign
    DROP COLUMN `requires_payment`;
