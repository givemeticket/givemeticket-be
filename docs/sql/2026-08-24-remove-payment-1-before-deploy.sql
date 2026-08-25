-- 결제 개념 제거 — 1단계. 배포 "전"에 돌린다.
--
-- 구 코드와 신 코드가 함께 돌아갈 수 있는 상태로 만든다. 컬럼은 아직 지우지 않는다.
-- 여기서 지워버리면 아직 돌고 있는 구 코드가 행사를 만들지 못한다.
--
-- 이 스크립트를 돌린 뒤에도 구 코드는 지금까지처럼 동작한다. 안심하고 먼저 돌려도 된다.
-- 여러 번 돌려도 결과는 같다.
--
-- 적용 (서버):
--   cd ~/givemeticket-prod
--   docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
--     < 2026-08-24-remove-payment-1-before-deploy.sql
--
-- 적용 (로컬):
--   docker compose exec -T mysql mysql -ugivemeticket -pgivemeticket givemeticket \
--     < docs/sql/2026-08-24-remove-payment-1-before-deploy.sql

-- 1. 빠진 값을 DB 가 채워주게 한다.
--    requires_payment 와 reconcile_attempts 는 NOT NULL 인데 기본값이 없다. 신 코드는 이 컬럼들을
--    아예 모른 채 INSERT 하므로, 기본값을 주지 않으면 행사 생성과 신청이 그대로 실패한다.
--    구 코드는 지금처럼 값을 직접 넣으므로 영향이 없다.
ALTER TABLE campaign
    ALTER COLUMN `requires_payment` SET DEFAULT b'0';

ALTER TABLE application
    ALTER COLUMN `reconcile_attempts` SET DEFAULT 0;

-- 2. 사라진 enum 상수를 쓰는 기존 행을 옮긴다. 자리를 잡고 있던 상태
--    (PENDING/UNKNOWN/MANUAL_REVIEW)는 CONFIRMED 로 올린다. Redis 재고는 이 신청들 몫을
--    이미 빼둔 상태라, 이렇게 해야 재고와 신청 수가 어긋나지 않는다.
--    CONFIRMED/CANCELLED 는 구 코드에서도 유효한 값이라 지금 돌려도 안전하다.
UPDATE application
   SET status = 'CONFIRMED',
       failure_reason = NULL
 WHERE status IN ('PENDING', 'UNKNOWN', 'MANUAL_REVIEW');

-- 3. 자리를 놓친 상태(FAILED)는 CANCELLED 로 맞춘다. 둘 다 재고를 잡고 있지 않다.
UPDATE application
   SET status = 'CANCELLED'
 WHERE status = 'FAILED';

-- 4. 남아 있는 결제·만료 사유를 비운다. CAMPAIGN_DELETED / USER_WITHDRAWN 만 살아남는다.
UPDATE application
   SET failure_reason = NULL
 WHERE failure_reason IN ('SOLD_OUT', 'PAYMENT_DECLINED', 'PAYMENT_ERROR', 'EXPIRED');

SELECT '1단계 완료 — 이제 배포해도 됩니다. 배포가 끝나면 2단계를 돌리세요.' AS result;
