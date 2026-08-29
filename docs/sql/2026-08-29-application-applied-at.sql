-- 신청 시각(applied_at) 백필 — 배포가 "끝난 뒤"에 돌린다.
--
-- 주최자의 신청자 목록이 "신청한 순서"로 정렬돼야 해서 application 에 applied_at 이 생겼다.
-- created_at 은 행이 만들어진 시각인데, 영속화가 비동기라 그건 워커가 큐에서 꺼낸 시각이다.
-- 재시도가 끼면 실제 신청보다 한참 뒤가 될 수 있어 선착순 순서를 말할 수 없다.
-- 그래서 좌석을 잡은 시각(ReservationEvent.occurredAt)을 따로 남긴다.
--
-- 컬럼과 인덱스 자체는 ddl-auto=update 가 만들어 준다. 여기서는 기존 행의 빈 값만 채운다.
--
-- 배포 "전"에 돌릴 필요는 없다. 컬럼이 없는 상태에서 돌리면 그냥 실패한다.
-- 백필 전에도 조회는 동작한다 — 값이 비어 있으면 Application.appliedAt() 이 created_at 을
-- 대신 내려주고, 정렬에서는 MySQL 이 NULL 을 맨 앞(= 가장 오래된 신청)에 놓는다.
--
-- 여러 번 돌려도 된다.
--
-- 적용 (서버):
--   cd ~/givemeticket-prod
--   docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
--     < 2026-08-29-application-applied-at.sql
--
-- 적용 (로컬):
--   docker compose exec -T mysql mysql -ugivemeticket -pgivemeticket givemeticket \
--     < docs/sql/2026-08-29-application-applied-at.sql

-- 1. 얼마나 채워야 하는지 먼저 본다.
SELECT COUNT(*) AS rows_to_backfill FROM application WHERE applied_at IS NULL;

-- 2. 옛 행은 생성 시각으로 채운다. 큐가 붙기 전에는 신청과 저장이 같은 요청 안에서
--    일어났으므로 created_at 이 곧 신청 시각이다.
UPDATE application
   SET applied_at = created_at
 WHERE applied_at IS NULL;

SELECT '백필 완료' AS result, COUNT(*) AS remaining
  FROM application WHERE applied_at IS NULL;
