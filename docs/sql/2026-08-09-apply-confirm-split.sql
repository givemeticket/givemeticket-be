-- apply / confirm 분리에 따른 스키마 변경.
--
-- 새로 만드는 DB는 엔티티 매핑대로 생성되므로 필요 없다.
-- 이미 데이터가 있는 DB에만 한 번 돌린다.
--
-- 적용:
--   docker compose exec -T mysql mysql -ugivemeticket -p givemeticket < docs/sql/2026-08-09-apply-confirm-split.sql

-- 만료 sweeper가 10초마다 PENDING + expires_at 으로 조회한다.
-- 인덱스가 없으면 신청이 쌓일수록 주기마다 풀스캔이 된다.
CREATE INDEX idx_application_status_expires ON application (status, expires_at);
