#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:?사용법: ./deploy.sh <backend|payment-mock>}"

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
HEALTH_RETRIES="${HEALTH_RETRIES:-120}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"

case "$SERVICE" in
  backend)      HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health/readiness}" ;;
  payment-mock) HEALTH_URL="" ;;
  *) echo "❌ 알 수 없는 서비스: $SERVICE (backend | payment-mock)"; exit 1 ;;
esac

cd "$APP_DIR"

echo "🧱 의존 서비스 기동 확인 (이미 떠 있고 변경 없으면 아무 것도 하지 않음)..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps mysql redis

if [[ -f "${APP_DIR}/.env" ]]; then
  set -a; . "${APP_DIR}/.env"; set +a
fi
IMAGE_REF="${DOCKER_NAMESPACE:?.env에 DOCKER_NAMESPACE가 필요합니다}/givemeticket:${SERVICE}"
PREV_REF="${IMAGE_REF}-prev"

echo "🏷  현재 이미지를 ${PREV_REF} 로 보존..."
if docker image inspect "$IMAGE_REF" >/dev/null 2>&1; then
  docker rmi -f "$PREV_REF" >/dev/null 2>&1 || true
  docker tag "$IMAGE_REF" "$PREV_REF"
else
  echo "   (첫 배포라 이전 이미지 없음)"
fi

echo "🔻 ${SERVICE} 최신 이미지 받는 중..."
docker compose -f "$COMPOSE_FILE" pull "$SERVICE"

echo "🚀 ${SERVICE} 재기동..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate "$SERVICE"

echo "📊 관측 스택 반영..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps prometheus grafana

if [[ -f "${APP_DIR}/certbot/conf/live/${DOMAIN:-api.givemeticket.site}/fullchain.pem" ]]; then
  echo "🔀 nginx 반영..."
  docker compose -f "$COMPOSE_FILE" up -d --no-deps nginx certbot
else
  echo "⚠️  인증서가 없어 nginx를 건너뜁니다. nginx/init-letsencrypt.sh 를 먼저 실행하세요."
fi

cleanup_images() {
  echo "🧹 이미지 정리 (최신 + 직전만 유지)..."
  docker image prune -f >/dev/null 2>&1 || true
  docker builder prune -af >/dev/null 2>&1 || true
  df -h / | tail -1 | awk '{print "   디스크: " $3 " / " $2 " (" $5 ")"}'
}

if [[ -z "$HEALTH_URL" ]]; then
  echo "✅ ${SERVICE} 재기동 완료 (헬스체크 대상 아님)"
  cleanup_images
  exit 0
fi

echo "🩺 헬스체크 (${HEALTH_URL}, 최대 $((HEALTH_RETRIES * HEALTH_INTERVAL))초 대기)..."
for i in $(seq 1 "$HEALTH_RETRIES"); do
  if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    echo "✅ 배포 성공 (시도 ${i}/${HEALTH_RETRIES})"
    cleanup_images
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
