#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:?사용법: ./deploy.sh <backend|payment-mock>}"

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
HEALTH_RETRIES="${HEALTH_RETRIES:-120}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"

case "$SERVICE" in
  backend)      HEALTH_URL="${HEALTH_URL:-http://localhost/actuator/health/readiness}" ;;
  payment-mock) HEALTH_URL="" ;;
  *) echo "❌ 알 수 없는 서비스: $SERVICE (backend | payment-mock)"; exit 1 ;;
esac

cd "$APP_DIR"

echo "🧱 의존 서비스 기동 확인 (이미 떠 있고 변경 없으면 아무 것도 하지 않음)..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps mysql redis

echo "🔻 ${SERVICE} 최신 이미지 받는 중..."
docker compose -f "$COMPOSE_FILE" pull "$SERVICE"

echo "🚀 ${SERVICE} 재기동..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate "$SERVICE"

echo "📊 관측 스택 반영..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps prometheus grafana

if [[ -z "$HEALTH_URL" ]]; then
  echo "✅ ${SERVICE} 재기동 완료 (헬스체크 대상 아님)"
  docker image prune -f >/dev/null 2>&1 || true
  exit 0
fi

echo "🩺 헬스체크 (${HEALTH_URL}, 최대 $((HEALTH_RETRIES * HEALTH_INTERVAL))초 대기)..."
for i in $(seq 1 "$HEALTH_RETRIES"); do
  if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    echo "✅ 배포 성공 (시도 ${i}/${HEALTH_RETRIES})"
    docker image prune -f >/dev/null 2>&1 || true
    exit 0
  fi
  if (( i % 6 == 0 )); then
    echo "   ...아직 대기 중 (시도 ${i}/${HEALTH_RETRIES})"
  fi
  sleep "$HEALTH_INTERVAL"
done

echo "❌ 헬스체크 실패 — ${SERVICE} 최근 로그:"
docker compose -f "$COMPOSE_FILE" logs --tail=60 "$SERVICE"
exit 1
