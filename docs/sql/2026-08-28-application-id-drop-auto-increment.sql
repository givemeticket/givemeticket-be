-- 예매 식별자 채번을 Redis 로 옮김 — 배포가 "끝난 뒤"에 돌린다.
--
-- 신 코드는 application.id 를 Redis 카운터에서 받아 직접 채워 INSERT 한다. DB 가 더는
-- 번호를 발급하지 않으므로 컬럼의 AUTO_INCREMENT 를 뗀다. ddl-auto=update 는 이걸
-- 지워주지 않아서 손으로 돌려야 한다.
--
-- 배포 "전"에 돌리면 안 된다. 구 코드는 id 없이 INSERT 하므로 AUTO_INCREMENT 가 없으면
-- 그 자리에서 신청이 깨진다.
--
-- 배포는 deploy.sh 가 컨테이너를 --force-recreate 로 갈아끼우는 단일 인스턴스 방식이라
-- 구/신 코드가 함께 도는 창이 없다. 그래서 2026-08-24 결제 컬럼 제거와 달리 단계를
-- 나눌 필요가 없다. 여러 대를 굴리게 되면 이 전제부터 다시 봐야 한다.
--
-- Redis 카운터 시딩은 손대지 않아도 된다. 애플리케이션이 기동할 때 ApplicationIdSeeder 가
-- MAX(id) 이상으로 끌어올린다(이미 더 높으면 그대로 둔다).
--
-- 여러 번 돌려도 된다.
--
-- 적용 (서버):
--   cd ~/givemeticket-prod
--   docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
--     < 2026-08-28-application-id-drop-auto-increment.sql
--
-- 적용 (로컬):
--   docker compose exec -T mysql mysql -ugivemeticket -pgivemeticket givemeticket \
--     < docs/sql/2026-08-28-application-id-drop-auto-increment.sql

-- 1. 확인용. 지금 카운터가 어디까지 가 있는지 남겨둔다.
SELECT COALESCE(MAX(id), 0) AS max_application_id FROM application;

-- 2. AUTO_INCREMENT 제거. 타입과 NOT NULL 은 그대로 둔다.
--    남겨두면 언젠가 id 없이 들어온 INSERT 에 MySQL 이 조용히 번호를 붙이고,
--    그 번호가 Redis 카운터가 발급할 번호와 부딪힌다.
ALTER TABLE application MODIFY COLUMN id BIGINT NOT NULL;
