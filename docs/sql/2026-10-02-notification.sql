-- 사용자 알림 — 배포 "뒤"에 돌린다.
--
-- notification, notification_outbox 두 테이블과 campaign.detail_event_at 인덱스는
-- ddl-auto=update 가 만들어 준다. 아래 DDL 은 무엇이 생기는지 남겨두는 기록이다.
--
-- 손으로 할 일은 하나다. Hibernate 가 새 테이블을 만들 때 type 컬럼에 enum 값 목록으로
-- CHECK 제약을 건다. 그런데 나중에 NotificationType 에 값을 더해도 ddl-auto=update 는
-- 이 제약을 고쳐주지 않아서, 새 종류의 알림을 넣는 순간 INSERT 가 깨진다. 기존 테이블
-- (application 등)에는 이 제약이 없다 — 지금 매핑으로 바뀌기 전에 만들어진 테이블이라서다.
-- 그래서 3번에서 제약을 지워 기존 테이블과 맞춘다. 값의 정합성은 애플리케이션의 enum 이 지킨다.
--
-- 3번은 한 번만 돌린다. 이미 지운 뒤에 다시 돌리면 "check constraint ... does not exist" 로
-- 실패하는데, 원하는 상태는 그대로라 무시해도 된다.
--
-- 전제: MySQL 8.0 이상. 팬아웃 워커가 원본을 FOR UPDATE SKIP LOCKED 로 잡는다.
--
-- 확인 (서버):
--   cd ~/givemeticket-prod
--   docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
--     < 2026-10-02-notification.sql

-- 생기는 것 (참고용, 주석 처리)
--
-- CREATE TABLE notification (
--     id          BIGINT AUTO_INCREMENT PRIMARY KEY,
--     user_id     BIGINT       NOT NULL,
--     campaign_id BIGINT       NOT NULL,
--     type        VARCHAR(32)  NOT NULL,
--     payload     JSON         NOT NULL,
--     dedupe_key  VARCHAR(100) NULL,
--     read_at     DATETIME(6)  NULL,
--     created_at  DATETIME(6)  NULL,
--     updated_at  DATETIME(6)  NULL,
--     UNIQUE KEY uk_notification_user_dedupe (user_id, dedupe_key),
--     KEY idx_notification_user_id (user_id, id),
--     KEY idx_notification_user_read (user_id, read_at),
--     KEY idx_notification_created_at (created_at)
-- );
--
-- CREATE TABLE notification_outbox (
--     id           BIGINT AUTO_INCREMENT PRIMARY KEY,
--     campaign_id  BIGINT       NOT NULL,
--     type         VARCHAR(32)  NOT NULL,
--     payload      JSON         NOT NULL,
--     dedupe_key   VARCHAR(100) NULL,
--     processed_at DATETIME(6)  NULL,
--     created_at   DATETIME(6)  NULL,
--     updated_at   DATETIME(6)  NULL,
--     UNIQUE KEY uk_notification_outbox_dedupe (dedupe_key),
--     KEY idx_notification_outbox_processed (processed_at, id)
-- );
--
-- CREATE INDEX idx_campaign_event_at ON campaign (detail_event_at);

-- 1. 인덱스가 다 생겼는지 본다.
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND (table_name IN ('notification', 'notification_outbox')
        OR index_name = 'idx_campaign_event_at')
 GROUP BY table_name, index_name;

-- 2. 펼치지 못하고 쌓여 있는 원본. 평소에는 0 이어야 한다.
--    오래된 행이 남아 있으면 워커가 같은 원본에서 계속 실패하고 있다는 뜻이다(로그의
--    "notification fan-out failed" 를 본다).
SELECT COUNT(*) AS pending, MIN(created_at) AS oldest
  FROM notification_outbox
 WHERE processed_at IS NULL;

-- 3. type 컬럼의 CHECK 제약을 지운다. 이름은 MySQL 이 붙인 것이라 먼저 확인한다.
SELECT t.TABLE_NAME, t.CONSTRAINT_NAME, c.CHECK_CLAUSE
  FROM information_schema.TABLE_CONSTRAINTS t
  JOIN information_schema.CHECK_CONSTRAINTS c
    ON c.CONSTRAINT_SCHEMA = t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME = t.CONSTRAINT_NAME
 WHERE t.CONSTRAINT_SCHEMA = DATABASE()
   AND t.TABLE_NAME IN ('notification', 'notification_outbox');

ALTER TABLE notification DROP CHECK notification_chk_1;
ALTER TABLE notification_outbox DROP CHECK notification_outbox_chk_1;
