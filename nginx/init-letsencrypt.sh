#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

if [[ -f "${APP_DIR}/.env" ]]; then
  set -a; . "${APP_DIR}/.env"; set +a
fi

DOMAIN="${DOMAIN:?DOMAIN이 필요합니다 (.env 또는 환경변수)}"
EMAIL="${CERTBOT_EMAIL:?CERTBOT_EMAIL 환경변수가 필요합니다}"
STAGING="${STAGING:-0}"

CERT_PATH="./certbot/conf/live/${DOMAIN}"

if [[ -d "$CERT_PATH" ]]; then
  echo "✅ 이미 인증서가 있습니다: $CERT_PATH"
  echo "   재발급하려면 이 디렉터리를 지우고 다시 실행하세요."
  exit 0
fi

echo "📁 certbot 디렉터리 준비..."
mkdir -p ./certbot/conf ./certbot/www

echo "🔧 nginx 기동용 임시 자체 서명 인증서 생성..."
mkdir -p "$CERT_PATH"
docker run --rm --entrypoint /bin/sh -v "$APP_DIR/certbot/conf:/etc/letsencrypt" certbot/certbot \
  -c "openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '/etc/letsencrypt/live/${DOMAIN}/privkey.pem' \
    -out '/etc/letsencrypt/live/${DOMAIN}/fullchain.pem' \
    -subj '/CN=localhost'"

echo "🚀 nginx 기동..."
docker compose up -d nginx
sleep 5

echo "🗑  임시 인증서 삭제..."
docker run --rm --entrypoint /bin/rm -v "$APP_DIR/certbot/conf:/etc/letsencrypt" certbot/certbot \
  -rf "/etc/letsencrypt/live/${DOMAIN}" \
         "/etc/letsencrypt/archive/${DOMAIN}" \
         "/etc/letsencrypt/renewal/${DOMAIN}.conf"

echo "📜 Let's Encrypt 인증서 발급 (staging=${STAGING})..."
STAGING_ARG=""
if [[ "$STAGING" != "0" ]]; then
  STAGING_ARG="--staging"
fi

docker run --rm \
  -v "$APP_DIR/certbot/conf:/etc/letsencrypt" \
  -v "$APP_DIR/certbot/www:/var/www/certbot" \
  certbot/certbot certonly --webroot -w /var/www/certbot \
    $STAGING_ARG \
    --email "$EMAIL" \
    -d "$DOMAIN" \
    --rsa-key-size 2048 \
    --agree-tos \
    --no-eff-email \
    --non-interactive

echo "🔄 nginx 리로드..."
docker compose exec nginx nginx -s reload

echo "✅ 완료. https://${DOMAIN} 로 접속됩니다."
