#!/usr/bin/env bash
# 부하 테스트 중 서버 자원 점유를 1초 간격으로 샘플링한다.
#
# k6 는 클라이언트에서 본 응답 시간만 알려준다. "왜 느려졌는지" 는 서버 쪽
# 스레드·커넥션 점유를 같이 봐야 보인다.
#
#   ./load-test/monitor.sh out.tsv &
#   k6 run load-test/baseline.js
#   kill %1
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:18080}"
OUT="${1:-/dev/stdout}"
INTERVAL="${INTERVAL:-1}"

metric() {
  curl -s --max-time 2 "${BASE_URL}/actuator/metrics/$1" \
    | sed -n 's/.*"measurements":\[{"statistic":"[A-Z_]*","value":\([0-9.E-]*\)}.*/\1/p'
}

printf 'time\ttomcat_busy\ttomcat_current\thikari_active\thikari_pending\tjvm_threads\n' > "$OUT"

while true; do
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date +%H:%M:%S)" \
    "$(metric tomcat.threads.busy)" \
    "$(metric tomcat.threads.current)" \
    "$(metric hikaricp.connections.active)" \
    "$(metric hikaricp.connections.pending)" \
    "$(metric jvm.threads.live)" >> "$OUT"
  sleep "$INTERVAL"
done
