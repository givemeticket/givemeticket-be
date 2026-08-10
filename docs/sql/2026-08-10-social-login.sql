-- 소셜 로그인 도입에 필요한 스키마 변경.
--
-- users 테이블을 이미 만든 적이 있다면 provider_id 가 bigint 로 잡혀 있다.
-- ddl-auto=update 는 컬럼 "타입"을 바꾸지 않으므로 그대로 두면 네이버 회원번호(영숫자 문자열)를
-- 저장할 때 실패한다. 카카오만 쓰던 시점에 만든 DB 라면 이 스크립트를 한 번 돌려야 한다.
-- users 테이블이 아직 없는 DB 는 엔티티 매핑대로 생성되므로 필요 없다.
--
-- 적용:
--   docker compose exec -T mysql mysql -ugivemeticket -p givemeticket < docs/sql/2026-08-10-social-login.sql

-- 1. provider_id 를 문자열로. 카카오 회원번호(숫자)는 그대로 문자열이 되므로 데이터 유실이 없다.
ALTER TABLE users
    MODIFY COLUMN `provider_id` varchar(64)  NOT NULL,
    MODIFY COLUMN `provider`    varchar(20)  NOT NULL;

-- 2. 프로필 이미지. 없는 계정도 있으므로 NULL 을 허용한다.
--    ddl-auto=update 가 컬럼 "추가"는 해주므로 새로 만드는 DB 는 필요 없다.
ALTER TABLE users
    ADD COLUMN `profile_image_url` varchar(500) NULL;

-- 3. (provider, provider_id) 유니크. 같은 소셜 계정의 중복 가입을 DB 에서 막는다.
--    ddl-auto 가 만들어주지 않는 경우가 있어 명시한다.
--    (이미 있으면 "Duplicate key name" 이 나므로 무시해도 된다)
CREATE UNIQUE INDEX uk_users_provider ON users (provider, provider_id);
