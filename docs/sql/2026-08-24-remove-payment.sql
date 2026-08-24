-- 결제 개념 제거에 따른 스키마 변경.
--
-- ddl-auto=update 는 컬럼과 인덱스를 "추가"만 한다. 쓰지 않게 된 것들은 그대로 남고,
-- 특히 campaign.requires_payment 는 NOT NULL 이라 기본값 없이 두면 행사 생성이 그대로 실패한다.
-- 이미 데이터가 있는 DB 는 이 스크립트를 한 번 돌려야 한다.
-- 새로 만드는 DB 는 엔티티 매핑대로 생성되므로 필요 없다.
--
-- 적용:
--   docker compose exec -T mysql mysql -ugivemeticket -p givemeticket < docs/sql/2026-08-24-remove-payment.sql

-- 1. 사라진 enum 상수를 쓰는 기존 행부터 정리한다. 컬럼을 먼저 지우면 판단 근거가 없어진다.
--    자리를 잡고 있던 상태(PENDING/UNKNOWN/MANUAL_REVIEW)는 CONFIRMED 로 올린다.
--    Redis 재고는 이 신청들 몫을 이미 빼둔 상태라, 이렇게 해야 재고와 신청 수가 어긋나지 않는다.
UPDATE application
   SET status = 'CONFIRMED',
       failure_reason = NULL
 WHERE status IN ('PENDING', 'UNKNOWN', 'MANUAL_REVIEW');

-- 2. 자리를 놓친 상태(FAILED)는 CANCELLED 로 맞춘다. 둘 다 재고를 잡고 있지 않다.
UPDATE application
   SET status = 'CANCELLED'
 WHERE status = 'FAILED';

-- 3. 남아 있는 결제·만료 사유를 비운다. CAMPAIGN_DELETED / USER_WITHDRAWN 만 살아남는다.
UPDATE application
   SET failure_reason = NULL
 WHERE failure_reason IN ('SOLD_OUT', 'PAYMENT_DECLINED', 'PAYMENT_ERROR', 'EXPIRED');

-- 4. 결제 홀드가 없어져 만료 sweeper 도 없다. 그 조회를 위해 만든 인덱스를 지운다.
DROP INDEX idx_application_status_expires ON application;

-- 5. 결제 흔적 컬럼 제거.
ALTER TABLE application
    DROP COLUMN `payment_key`,
    DROP COLUMN `transaction_id`,
    DROP COLUMN `expires_at`,
    DROP COLUMN `payment_requested_at`,
    DROP COLUMN `reconcile_attempts`;

-- 6. 행사에는 더 이상 "결제가 필요한지"가 없다.
ALTER TABLE campaign
    DROP COLUMN `requires_payment`;
