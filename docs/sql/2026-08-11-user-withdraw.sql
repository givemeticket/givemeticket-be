-- 회원 탈퇴에 필요한 스키마 변경.
--
-- ddl-auto=update 는 컬럼을 "추가"만 한다. NOT NULL 을 NULL 로 푸는 것은 해주지 않으므로
-- 이미 users 테이블이 있는 DB 는 이 스크립트를 한 번 돌려야 한다.
-- 새로 만드는 DB 는 엔티티 매핑대로 생성되므로 필요 없다.
--
-- 적용:
--   docker compose exec -T mysql mysql -ugivemeticket -p givemeticket < docs/sql/2026-08-11-user-withdraw.sql

-- 1. 탈퇴 시각. NULL 이면 활성 회원이다.
ALTER TABLE users
    ADD COLUMN `deleted_at` datetime(6) NULL;

-- 2. provider_id 의 NOT NULL 을 푼다.
--    탈퇴하면 이 값을 비워 (provider, provider_id) 유니크 자리를 반납한다.
--    MySQL 은 유니크 제약에서 NULL 을 서로 다른 값으로 보므로, 탈퇴 회원이 여럿이어도 충돌하지 않고
--    같은 소셜 계정으로 새로 가입할 수 있다.
ALTER TABLE users
    MODIFY COLUMN `provider_id` varchar(64) NULL;
