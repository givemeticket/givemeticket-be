-- 캠페인 API 확장에 필요한 스키마 변경.
--
-- ddl-auto=update 는 컬럼을 "추가"만 한다. 기존 컬럼의 타입은 건드리지 않기 때문에,
-- 이전에 네이티브 ENUM 으로 만들어진 컬럼에는 새 상수(DELETED / UNKNOWN / MANUAL_REVIEW)가
-- 들어가지 못하고 런타임에 실패한다. 이미 데이터가 있는 DB에서는 이 스크립트를 한 번 돌려야 한다.
-- 새로 만드는 DB는 엔티티 매핑(@JdbcTypeCode(VARCHAR))대로 생성되므로 필요 없다.
--
-- 적용:
--   docker compose exec -T mysql mysql -ugivemeticket -p givemeticket < docs/sql/2026-08-09-campaign-api.sql

-- 1. ENUM -> VARCHAR. 앞으로 상수가 늘어도 컬럼 변경이 필요 없다.
ALTER TABLE campaign
    MODIFY COLUMN `type`   varchar(32) NOT NULL,
    MODIFY COLUMN `status` varchar(32) NOT NULL;

ALTER TABLE application
    MODIFY COLUMN `status`         varchar(32) NOT NULL,
    MODIFY COLUMN `failure_reason` varchar(32) NULL;

-- 2. short_code 가 없던 시절의 행은 빈 문자열로 채워져 있어 그대로는 유니크를 걸 수 없다.
--    지우지 않고 id 기반으로 채운다. 이 링크로 접근할 일은 없지만 행은 보존된다.
UPDATE campaign SET short_code = CONCAT('legacy', id) WHERE short_code = '' OR short_code IS NULL;

-- 3. short_code 유니크. ddl-auto 는 유니크 인덱스를 만들어주지 않는 경우가 있어 명시한다.
--    (이미 있으면 "Duplicate key name" 이 나므로 무시해도 된다)
CREATE UNIQUE INDEX uk_campaign_short_code ON campaign (short_code);

-- 4. uk_application_campaign_user 가 같은 컬럼을 같은 순서로 덮으므로 중복 인덱스를 제거한다.
DROP INDEX idx_application_campaign_user ON application;
