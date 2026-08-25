-- 결제 개념 제거 — 2단계. 배포가 "끝난 뒤"에 돌린다.
--
-- 여기부터는 신 코드만 돌고 있어야 한다. 구 코드는 이 스크립트가 지우는 컬럼들을 아직 읽기 때문에,
-- 배포 전에 돌리면 신청 조회가 그 자리에서 깨진다.
--
-- 배포 직후 바로 돌리는 게 좋다. 1단계와 배포 사이에 구 코드가 새로 만든 PENDING 이 남아 있으면,
-- 그 신청을 조회하는 사용자는 2단계가 돌 때까지 500 을 본다.
--
-- 여러 번 돌려도 되지만, 두 번째부터는 이미 지운 컬럼 때문에 에러가 난다(그 시점엔 이미 끝난 것이다).
--
-- 적용 (서버):
--   cd ~/givemeticket-prod
--   docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
--     < 2026-08-24-remove-payment-2-after-deploy.sql
--
-- 적용 (로컬):
--   docker compose exec -T mysql mysql -ugivemeticket -pgivemeticket givemeticket \
--     < docs/sql/2026-08-24-remove-payment-2-after-deploy.sql

-- 1. 1단계와 배포 사이에 구 코드가 새로 만든 PENDING 이 있을 수 있다.
--    신 코드는 이 값을 읽지 못하므로 한 번 더 훑는다. 1단계의 2~4 와 같은 문장이다.
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

-- 2. 결제 홀드가 없어져 만료 sweeper 도 없다. 그 조회를 위해 만든 인덱스를 지운다.
DROP INDEX idx_application_status_expires ON application;

-- 3. 결제 흔적 컬럼 제거.
ALTER TABLE application
    DROP COLUMN `payment_key`,
    DROP COLUMN `transaction_id`,
    DROP COLUMN `expires_at`,
    DROP COLUMN `payment_requested_at`,
    DROP COLUMN `reconcile_attempts`;

-- 4. 행사에는 더 이상 "결제가 필요한지"가 없다.
ALTER TABLE campaign
    DROP COLUMN `requires_payment`;

SELECT '2단계 완료 — 결제 흔적이 모두 제거됐습니다.' AS result;
