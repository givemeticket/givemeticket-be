#!/usr/bin/env bash
# 조회 부하를 계단식으로 올리면서, 계단마다 처리량과 GC 를 따로 기록한다.
#
#   caffeinate -i ./load-test/ramp.sh                        # 세 캐시 구성 전부
#   MODES=redis,local ./load-test/ramp.sh                    # 골라서
#   RATES=10,20,40 STEP=45s ./load-test/ramp.sh              # 계단 조절
#
# gc-matrix.sh 와 다른 점은 한 실행 안에서 부하를 여러 단계로 올리고, 각 단계를 별도
# 구간으로 잰다는 것이다. "어느 지점에서 한계가 오는지"와 "그때 GC 가 어떻게 되는지"를 본다.
set -euo pipefail

cd "$(dirname "$0")/.."

BASE_URL=${BASE_URL:-http://localhost:18080}
PROM_URL=${PROM_URL:-http://localhost:9090}
GRAFANA_URL=${GRAFANA_URL:-http://localhost:3001}
SCRIPT=${SCRIPT:-load-test/read-only.js}
# 계단별 목표 도착률(초당 요청 수)과 각 계단을 유지하는 시간.
RATES=${RATES:-10,20,40,80,160}
STEP=${STEP:-60s}
# 비교할 캐시 구성.
MODES=${MODES:-none,redis,local}
DETAIL_SIZE=${DETAIL_SIZE:-50000}
CAMPAIGNS=${CAMPAIGNS:-50}
# 백엔드에 주는 CPU. GC 워커 스레드 수를 결정하므로 결과 해석에 반드시 필요하다.
# compose 가 이 값을 읽어 간다. 결과 CSV 에도 같이 남긴다.
export BACKEND_CPUS=${BACKEND_CPUS:-1.0}
SETTLE=${SETTLE:-60}
# 계단이 바뀐 직후에는 도착률이 아직 안 잡혀 있다. 앞뒤를 잘라내고 잰다.
HEAD_TRIM=${HEAD_TRIM:-12}
TAIL_TRIM=${TAIL_TRIM:-5}

JOB='job="givemeticket-backend"'
if [ -f .env ]; then
  MYSQL_USER=${MYSQL_USER:-$(grep -m1 '^MYSQL_USER=' .env | cut -d= -f2-)}
  MYSQL_PASSWORD=${MYSQL_PASSWORD:-$(grep -m1 '^MYSQL_PASSWORD=' .env | cut -d= -f2-)}
  MYSQL_DATABASE=${MYSQL_DATABASE:-$(grep -m1 '^MYSQL_DATABASE=' .env | cut -d= -f2-)}
fi
JWT_SECRET_KEY=${JWT_SECRET_KEY:-$(grep -m1 '^JWT_SECRET_KEY=' .env 2>/dev/null | cut -d= -f2-)}

OUT_DIR="load-test/results/ramp-$(date +%Y%m%d-%H%M%S)"
CSV="$OUT_DIR/summary.csv"

need() { command -v "$1" >/dev/null || { echo "$1 이 필요하다"; exit 1; }; }
need k6; need curl; need python3; need docker

curl -sf "$PROM_URL/-/ready" >/dev/null || {
  echo "프로메테우스($PROM_URL)가 안 뜬다. docker compose --profile obs up -d 먼저."
  exit 1
}

to_seconds() {
  case "$1" in
    *h) echo $(( ${1%h} * 3600 )) ;;
    *m) echo $(( ${1%m} * 60 )) ;;
    *s) echo "${1%s}" ;;
    *) echo "$1" ;;
  esac
}

promq() {
  local query=$1 at=$2
  curl -sG "$PROM_URL/api/v1/query" \
    --data-urlencode "query=$query" --data-urlencode "time=$at" |
    python3 -c 'import json,sys; r=json.load(sys.stdin).get("data",{}).get("result",[]); print(r[0]["value"][1] if r else "")'
}

wait_healthy() {
  local started=$SECONDS deadline=$((SECONDS + 300))
  until curl -sf "$BASE_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
    ((SECONDS < deadline)) || { echo "backend 가 안 뜬다"; exit 1; }
    sleep 2
  done
  echo "  기동 완료 ($((SECONDS - started))s)"
}

reset_state() {
  local user=${MYSQL_USER:-givemeticket} pass=${MYSQL_PASSWORD:-givemeticket}
  local db=${MYSQL_DATABASE:-givemeticket}
  docker compose exec -T mysql \
    mysql -u"$user" -p"$pass" "$db" -e "TRUNCATE TABLE application; TRUNCATE TABLE campaign;" 2>/dev/null \
    || { echo "  DB 초기화 실패"; return 1; }
  docker compose exec -T redis sh -c \
    "redis-cli --scan --pattern 'campaign:*' | xargs -r redis-cli del" >/dev/null 2>&1
  echo "  DB·Redis 초기화 완료"
}

run_mode() {
  local mode=$1 first=$2
  echo
  echo "=== 캐시 모드: $mode | backend CPU: $BACKEND_CPUS ==="
  if [ "$first" != "true" ] && ((SETTLE > 0)); then
    echo "  ${SETTLE}s 쉰다"
    sleep "$SETTLE"
  fi
  reset_state

  CAMPAIGN_CACHE_MODE="$mode" docker compose up -d --force-recreate backend >/dev/null
  wait_healthy
  sleep 10

  local out="$OUT_DIR/$mode.k6.txt"
  k6 run -e BASE_URL="$BASE_URL" -e RATES="$RATES" -e STEP="$STEP" \
    -e DETAIL_SIZE="$DETAIL_SIZE" -e CAMPAIGNS="$CAMPAIGNS" \
    -e JWT_SECRET_KEY="$JWT_SECRET_KEY" "$SCRIPT" >"$out" 2>&1 \
    || echo "  (k6 비정상 종료 — $out 확인)"

  # 부하가 실제로 시작한 시각. setup 이 끝나는 순간을 k6 가 직접 찍는다.
  local start_ms
  start_ms=$(grep -aoE 'SCENARIO_START [0-9]+' "$out" | head -1 | awk '{print $2}')
  if [ -z "$start_ms" ]; then
    echo "  부하 시작 시각을 못 찾았다. setup 이 실패했을 가능성이 크다 — 이 모드는 버린다."
    return
  fi
  local start=$((start_ms / 1000))

  local checks
  checks=$(grep -a 'checks_succeeded' "$out" | head -1 | grep -oE '[0-9]+\.[0-9]+%' | head -1 | tr -d '%')
  if [ -n "$checks" ] && awk "BEGIN{exit !($checks < 50)}"; then
    echo "  체크 성공률 ${checks}% — 부하가 제대로 안 걸렸다. 이 모드는 버린다."
    return
  fi

  local step_s idx=0
  step_s=$(to_seconds "$STEP")

  local IFS=','
  for target in $RATES; do
    unset IFS
    local win_start=$((start + idx * step_s + HEAD_TRIM))
    local win_end=$((start + (idx + 1) * step_s - TAIL_TRIM))
    local win=$((win_end - win_start))
    idx=$((idx + 1))

    local rps p95 p99 alloc gc_count pause_sum overhead cpu hit_local hit_redis promoted heap_max
    rps=$(promq "sum(increase(http_server_requests_seconds_count{$JOB}[${win}s] @ $win_end))/$win" "$win_end")
    p95=$(promq "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{$JOB}[${win}s] @ $win_end)) by (le))" "$win_end")
    p99=$(promq "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{$JOB}[${win}s] @ $win_end)) by (le))" "$win_end")
    alloc=$(promq "sum(increase(jvm_gc_memory_allocated_bytes_total{$JOB}[${win}s] @ $win_end))" "$win_end")
    promoted=$(promq "sum(increase(jvm_gc_memory_promoted_bytes_total{$JOB}[${win}s] @ $win_end))" "$win_end")
    gc_count=$(promq "sum(increase(jvm_gc_pause_seconds_count{$JOB}[${win}s] @ $win_end))" "$win_end")
    pause_sum=$(promq "sum(increase(jvm_gc_pause_seconds_sum{$JOB}[${win}s] @ $win_end))" "$win_end")
    overhead=$(promq "max_over_time(jvm_gc_overhead{$JOB}[${win}s] @ $win_end)" "$win_end")
    cpu=$(promq "avg_over_time(process_cpu_usage{$JOB}[${win}s] @ $win_end)" "$win_end")
    heap_max=$(promq "max_over_time(sum(jvm_memory_used_bytes{$JOB, area=\"heap\"})[${win}s:5s] @ $win_end)" "$win_end")
    hit_local=$(promq "sum(increase(campaign_cache_requests_total{$JOB, tier=\"local\", result=\"hit\"}[${win}s] @ $win_end))" "$win_end")
    hit_redis=$(promq "sum(increase(campaign_cache_requests_total{$JOB, tier=\"redis\", result=\"hit\"}[${win}s] @ $win_end))" "$win_end")

    echo "$mode,$BACKEND_CPUS,$target,$win_start,$win_end,$win,$rps,$p95,$p99,$alloc,$promoted,$gc_count,$pause_sum,$overhead,$cpu,$heap_max,$hit_local,$hit_redis" >>"$CSV"
    printf "  목표 %4s rps -> 실제 %6.1f rps, p99 %5.0fms, GC %2.0f회\n" \
      "$target" "${rps:-0}" "$(python3 -c "print(float('${p99:-0}' or 0)*1000)")" "${gc_count:-0}"
    local IFS=','
  done
  unset IFS

  # 포화 신호. k6 가 목표 도착률을 못 채워 버린 요청 수다. 0 이 아니면 그 지점이 한계다.
  # 버린 요청이 하나도 없으면 k6 는 이 줄 자체를 안 찍는다. grep 이 빈손으로 실패하면
  # set -e 가 스크립트를 끝내 버려서, 가장 잘 돌아간 모드에서 요약이 사라진다.
  local dropped
  dropped=$(grep -a 'dropped_iterations' "$out" 2>/dev/null | head -1 | grep -oE '[0-9]+' | head -1 || true)
  echo "  버려진 요청(전체): ${dropped:-0}"
  echo "  ${GRAFANA_URL}/d/givemeticket-jvm?from=${start}000&to=$((start + idx * step_s))000"
}

mkdir -p "$OUT_DIR"
echo "mode,backend_cpus,target_rate,window_start,window_end,window_s,rps,p95_s,p99_s,alloc_bytes,promoted_bytes,gc_count,pause_sum_s,gc_overhead,cpu,heap_used_max_bytes,cache_hit_local,cache_hit_redis" >"$CSV"

first=true
IFS=',' read -ra MODE_LIST <<<"$MODES"
for mode in "${MODE_LIST[@]}"; do
  run_mode "$mode" "$first"
  first=false
done

echo
echo "결과: $OUT_DIR"
python3 - "$CSV" <<'PY'
import csv, sys
rows = list(csv.DictReader(open(sys.argv[1])))
if not rows:
    sys.exit("측정된 행이 없다")

def f(v, d=0.0):
    try: return float(v)
    except (TypeError, ValueError): return d

cols = ["모드", "목표rps", "실제rps", "달성률", "p95(ms)", "p99(ms)", "할당(MB)", "할당/req(KB)",
        "GC(회)", "정지(s)", "오버헤드", "CPU", "힙피크(MB)", "캐시히트(local/redis)"]
out = []
for r in rows:
    rps, win, target = f(r["rps"]), f(r["window_s"]), f(r["target_rate"])
    reqs = rps * win
    out.append([
        r["mode"], f"{target:.0f}", f"{rps:.1f}",
        f"{rps / target * 100:.0f}%" if target else "-",
        f"{f(r['p95_s']) * 1000:.0f}", f"{f(r['p99_s']) * 1000:.0f}",
        f"{f(r['alloc_bytes']) / 1024 / 1024:.0f}",
        f"{f(r['alloc_bytes']) / reqs / 1024:.0f}" if reqs else "-",
        f"{f(r['gc_count']):.0f}", f"{f(r['pause_sum_s']):.2f}",
        f"{f(r['gc_overhead']) * 100:.1f}%", f"{f(r['cpu']) * 100:.0f}%",
        f"{f(r['heap_used_max_bytes']) / 1024 / 1024:.0f}",
        f"{f(r['cache_hit_local']):.0f}/{f(r['cache_hit_redis']):.0f}",
    ])

w = [max(len(str(x)) for x in [c] + [r[i] for r in out]) for i, c in enumerate(cols)]
line = lambda cells: "| " + " | ".join(str(c).ljust(w[i]) for i, c in enumerate(cells)) + " |"
print()
print(line(cols))
print("|" + "|".join("-" * (x + 2) for x in w) + "|")
prev = None
for r in out:
    if prev and r[0] != prev:
        print("|" + "|".join("-" * (x + 2) for x in w) + "|")
    print(line(r))
    prev = r[0]
PY

echo
echo "원래 설정으로 되돌리려면: docker compose up -d --force-recreate backend"
