#!/usr/bin/env bash
# 힙 크기 / GC 조합을 바꿔 가며 같은 부하를 반복하고, 구간별 GC 지표를 표로 뽑는다.
#
#   ./load-test/gc-matrix.sh                      # 기본 매트릭스 4개
#   ./load-test/gc-matrix.sh g1-256m par-256m     # 이름으로 골라서
#   DURATION=3m VUS=80 ./load-test/gc-matrix.sh   # 부하 조절
#
# 한 번 실행할 때마다 backend 컨테이너를 그 설정으로 다시 띄우기 때문에, 돌아가는 동안
# 서비스는 계속 재시작된다. 로컬에서만 쓴다.
set -euo pipefail

cd "$(dirname "$0")/.."

BASE_URL=${BASE_URL:-http://localhost:18080}
PROM_URL=${PROM_URL:-http://localhost:9090}
GRAFANA_URL=${GRAFANA_URL:-http://localhost:3001}
SCRIPT=${SCRIPT:-load-test/soak.js}
VUS=${VUS:-60}
DURATION=${DURATION:-2m}
# 행사 안내문 길이(자). 캐시에 담기는 값의 크기를 좌우한다. 0이면 detail 없이 만든다.
DETAIL_SIZE=${DETAIL_SIZE:-0}
# 초당 반복 수를 고정한다. 0이면 VU 고정(포화) 모드.
# A/B 비교는 반드시 RATE 를 줘서 같은 부하에서 재야 한다. 이유는 soak.js 주석 참고.
RATE=${RATE:-0}
# 한 실행이 끝나고 다음 실행을 시작하기 전에 쉬는 시간.
# 앞 실행의 뒷정리(MySQL 더티 페이지 플러시 등)가 다음 실행에 겹치면 뒤에 도는 쪽이 손해를 본다.
SETTLE=${SETTLE:-60}
# true 면 실행마다 부하 테스트 데이터를 지우고 시작한다. A/B 비교를 하려면 켜야 한다.
RESET_DB=${RESET_DB:-false}
# .env 의 DB 자격증명을 읽어 둔다 (초기화에 쓴다).
if [ -f .env ]; then
  MYSQL_USER=${MYSQL_USER:-$(grep -m1 '^MYSQL_USER=' .env | cut -d= -f2-)}
  MYSQL_PASSWORD=${MYSQL_PASSWORD:-$(grep -m1 '^MYSQL_PASSWORD=' .env | cut -d= -f2-)}
  MYSQL_DATABASE=${MYSQL_DATABASE:-$(grep -m1 '^MYSQL_DATABASE=' .env | cut -d= -f2-)}
fi
JOB='job="givemeticket-backend"'
# k6 가 액세스 토큰을 직접 서명한다. 서버와 같은 시크릿이어야 한다 (load-test/auth.js).
JWT_SECRET_KEY=${JWT_SECRET_KEY:-$(grep -m1 '^JWT_SECRET_KEY=' .env 2>/dev/null | cut -d= -f2-)}

# 이름 | 힙 옵션 | GC 옵션 | 컨테이너 메모리 | 추가 앱 환경변수
# (JVM 플래그에 ':' 가 들어가서 '|' 로 나눈다)
# 컨테이너 메모리를 그대로 두고 -Xmx 만 바꾸면 "힙 크기"만 딱 떼어서 비교할 수 있다.
DEFAULT_HEAP="-XX:InitialRAMPercentage=70.0 -XX:MaxRAMPercentage=70.0"
MATRIX=(
  # 캐시 도입 전후. 나머지 조건이 같아야 비교가 된다.
  "cache-off|$DEFAULT_HEAP|-XX:+UseG1GC|1g|CAMPAIGN_CACHE_ENABLED=false"
  "cache-on|$DEFAULT_HEAP|-XX:+UseG1GC|1g|CAMPAIGN_CACHE_ENABLED=true"
  # 힙 크기와 GC 종류. 캐시는 compose 기본값(켜짐)으로 돈다.
  "g1-256m|-Xms256m -Xmx256m|-XX:+UseG1GC|1g|"
  "g1-512m|-Xms512m -Xmx512m|-XX:+UseG1GC|1g|"
  "g1-700m|-Xms700m -Xmx700m|-XX:+UseG1GC|1g|"
  "par-256m|-Xms256m -Xmx256m|-XX:+UseParallelGC|1g|"
)

OUT_DIR="load-test/results/gc-$(date +%Y%m%d-%H%M%S)"
CSV="$OUT_DIR/summary.csv"

need() { command -v "$1" >/dev/null || { echo "$1 이 필요하다"; exit 1; }; }
need k6
need curl
need python3
need docker

curl -sf "$PROM_URL/-/ready" >/dev/null || {
  echo "프로메테우스($PROM_URL)가 안 뜬다. docker compose --profile obs up -d 먼저."
  exit 1
}

# 프로메테우스 instant query 하나를 실수 하나로 뽑는다. 값이 없으면 빈 문자열.
promq() {
  local query=$1 at=$2
  curl -sG "$PROM_URL/api/v1/query" \
    --data-urlencode "query=$query" \
    --data-urlencode "time=$at" |
    python3 -c '
import json,sys
r = json.load(sys.stdin).get("data",{}).get("result",[])
print(r[0]["value"][1] if r else "")
'
}

# 부하 테스트 직후에는 도커도 JVM 도 느리다. 기동에 3분 넘게 걸리는 일이 실제로 있어서
# 넉넉하게 잡는다. 여기서 잘리면 매트릭스 전체가 중간에 끊긴다.
# "3m" / "90s" / "1h" 를 초로 바꾼다.
to_seconds() {
  case "$1" in
    *h) echo $(( ${1%h} * 3600 )) ;;
    *m) echo $(( ${1%m} * 60 )) ;;
    *s) echo "${1%s}" ;;
    *) echo "$1" ;;
  esac
}

wait_healthy() {
  local started=$SECONDS
  local deadline=$((SECONDS + 300))
  until curl -sf "$BASE_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
    ((SECONDS < deadline)) || {
      echo "backend 가 $((SECONDS - started))s 동안 안 떴다. docker compose logs backend 확인."
      exit 1
    }
    sleep 2
  done
  echo "  기동 완료 ($((SECONDS - started))s)"
}

# 부하 테스트가 만든 데이터를 지운다.
#
# 안 지우면 실행할 때마다 application 행이 수천 건씩 쌓여서, 뒤에 도는 설정일수록
# 조회도 INSERT 도 느려진다. 그 상태로 A/B 를 비교하면 "나중에 돈 쪽이 나쁘다"를
# 설정 차이로 착각하게 된다. 실험 결과를 믿으려면 매 실행이 같은 데이터 크기에서 출발해야 한다.
#
# 기본은 꺼져 있다. 로컬 DB 의 캠페인·신청 데이터를 통째로 지우기 때문이다.
reset_state() {
  [ "$RESET_DB" = "true" ] || return 0

  local user=${MYSQL_USER:-givemeticket} pass=${MYSQL_PASSWORD:-givemeticket}
  local db=${MYSQL_DATABASE:-givemeticket}
  docker compose exec -T mysql \
    mysql -u"$user" -p"$pass" "$db" -e "TRUNCATE TABLE application; TRUNCATE TABLE campaign;" 2>/dev/null \
    || { echo "  DB 초기화 실패 — 자격증명(.env)을 확인하라"; return 1; }

  # 재고·상태·캐시 키가 남으면 지워진 캠페인을 가리키는 유령이 된다.
  docker compose exec -T redis sh -c \
    "redis-cli --scan --pattern 'campaign:*' | xargs -r redis-cli del" >/dev/null 2>&1

  echo "  DB·Redis 초기화 완료"
}

RUN_SEQ=0

run_one() {
  local name=$1 heap=$2 gc=$3 mem=$4 app_env=$5
  RUN_SEQ=$((RUN_SEQ + 1))
  # 같은 설정을 여러 번 돌려 순서 효과를 확인할 수 있게 산출물 이름에 순번을 붙인다.
  local tag="$RUN_SEQ-$name"

  echo
  echo "=== [$RUN_SEQ] $name | heap: $heap | gc: $gc | container: $mem${app_env:+ | $app_env} ==="
  if ((RUN_SEQ > 1)) && ((SETTLE > 0)); then
    echo "  ${SETTLE}s 쉰다 (앞 실행의 뒷정리가 겹치지 않게)"
    sleep "$SETTLE"
  fi
  reset_state

  # app_env 는 "KEY=VALUE KEY=VALUE" 라 단어 분리가 필요하다. 그래서 일부러 따옴표를 빼둔다.
  # shellcheck disable=SC2086
  env JVM_HEAP_OPTS="$heap" JVM_GC_OPTS="$gc" BACKEND_MEM_LIMIT="$mem" $app_env \
    docker compose up -d --force-recreate backend >/dev/null
  wait_healthy
  # JIT 가 덜 데워진 상태의 할당까지 세면 첫 설정만 손해를 본다. 잠깐 돌려서 맞춰 준다.
  sleep 10

  local t0 t1 win_start win_end win head_trim tail_trim
  t0=$(date +%s)
  k6 run -e BASE_URL="$BASE_URL" -e VUS="$VUS" -e DURATION="$DURATION" -e RATE="$RATE" \
    -e DETAIL_SIZE="$DETAIL_SIZE" \
    -e JWT_SECRET_KEY="$JWT_SECRET_KEY" "$SCRIPT" \
    >"$OUT_DIR/$tag.k6.txt" 2>&1 || echo "  (k6 임계값 실패 — $OUT_DIR/$tag.k6.txt 확인)"
  t1=$(date +%s)

  # 잘라낼 구간은 부하 모양에 따라 다르다.
  # VU 모드는 앞뒤로 30s 램프업 / 20s 램프다운이 붙고, 도착률 고정 모드는 그게 없다.
  if ((RATE > 0)); then
    head_trim=20
    tail_trim=5
  else
    head_trim=35
    tail_trim=25
  fi
  win_start=$((t0 + head_trim))
  win_end=$((t1 - tail_trim))
  win=$((win_end - win_start))
  if ((win < 30)); then
    echo "  측정 구간이 ${win}s 라 너무 짧다. DURATION 을 늘려라."
    return
  fi
  # 벽시계로만 구간을 재기 때문에, 도중에 맥이 자면 잠든 시간까지 구간에 들어간다.
  # 그러면 대부분이 유휴 상태인 창에서 지표를 뽑게 되므로 조용히 넘기면 안 된다.
  # 실행 전체를 caffeinate -i 로 감싸면 애초에 안 자게 할 수 있다.
  local expected=$(($(to_seconds "$DURATION") + 60))
  if ((win > expected * 2)); then
    echo "  측정 구간이 ${win}s 로 예상(~${expected}s)의 두 배가 넘는다."
    echo "  도중에 머신이 잤을 가능성이 크다. 이 실행은 버린다."
    return
  fi
  # 나중에 그래프로 되짚어 볼 수 있게 구간을 남긴다. 파일 시각으로 역산하지 않도록.
  local win_start_iso win_end_iso
  win_start_iso=$(python3 -c "import datetime,sys; print(datetime.datetime.fromtimestamp(int(sys.argv[1])).isoformat())" "$win_start")
  win_end_iso=$(python3 -c "import datetime,sys; print(datetime.datetime.fromtimestamp(int(sys.argv[1])).isoformat())" "$win_end")
  echo "  측정 구간: $win_start_iso ~ $win_end_iso (${win}s)"
  echo "  ${GRAFANA_URL}/d/givemeticket-jvm?from=${win_start}000&to=${win_end}000"

  local count pause_sum pause_max alloc promoted heap_max overhead rps p95 p99 xmx
  local read_p95 read_p99 read_rps hikari_pending hikari_active
  # 캐싱 단계마다 비교할 읽기 경로. 캐시가 먹으면 여기 지연과 커넥션 대기가 먼저 떨어진다.
  local read_uri='uri="/api/v1/campaigns/{shortCode}"'
  count=$(promq "sum(increase(jvm_gc_pause_seconds_count{$JOB}[${win}s]))" "$win_end")
  pause_sum=$(promq "sum(increase(jvm_gc_pause_seconds_sum{$JOB}[${win}s]))" "$win_end")
  pause_max=$(promq "max(max_over_time(jvm_gc_pause_seconds_max{$JOB}[${win}s]))" "$win_end")
  alloc=$(promq "sum(increase(jvm_gc_memory_allocated_bytes_total{$JOB}[${win}s]))" "$win_end")
  promoted=$(promq "sum(increase(jvm_gc_memory_promoted_bytes_total{$JOB}[${win}s]))" "$win_end")
  heap_max=$(promq "max_over_time(sum(jvm_memory_used_bytes{$JOB, area=\"heap\"})[${win}s:5s])" "$win_end")
  overhead=$(promq "max_over_time(jvm_gc_overhead{$JOB}[${win}s])" "$win_end")
  # jvm_gc_max_data_size_bytes 는 GC 가 한 번 돌아야 채워져서 초반에 비어 있을 수 있다.
  # G1 은 Eden/Survivor 의 max 가 -1 이라 그대로 더하면 안 되고, 양수인 풀만 골라 더한다.
  xmx=$(promq "sum(jvm_memory_max_bytes{$JOB, area=\"heap\"} > 0)" "$win_end")
  rps=$(promq "sum(increase(http_server_requests_seconds_count{$JOB}[${win}s])) / $win" "$win_end")
  p95=$(promq "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{$JOB}[${win}s])) by (le))" "$win_end")
  p99=$(promq "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{$JOB}[${win}s])) by (le))" "$win_end")

  read_p95=$(promq "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{$JOB, $read_uri}[${win}s])) by (le))" "$win_end")
  read_p99=$(promq "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{$JOB, $read_uri}[${win}s])) by (le))" "$win_end")
  read_rps=$(promq "sum(increase(http_server_requests_seconds_count{$JOB, $read_uri}[${win}s])) / $win" "$win_end")
  hikari_pending=$(promq "max_over_time(hikaricp_connections_pending{$JOB}[${win}s])" "$win_end")
  hikari_active=$(promq "max_over_time(hikaricp_connections_active{$JOB}[${win}s])" "$win_end")

  # 캐시를 끈 실행에서는 이 지표 자체가 없다. 빈 값이 그대로 표에 '-' 로 나간다.
  local cache_hit_rate cache_raw cache_compressed cache_get_avg
  cache_hit_rate=$(promq "sum(increase(campaign_cache_requests_total{$JOB, result=\"hit\"}[${win}s])) / sum(increase(campaign_cache_requests_total{$JOB}[${win}s]))" "$win_end")
  cache_raw=$(promq "sum(increase(campaign_cache_value_size_bytes_sum{$JOB, state=\"raw\"}[${win}s])) / sum(increase(campaign_cache_value_size_bytes_count{$JOB, state=\"raw\"}[${win}s]))" "$win_end")
  cache_compressed=$(promq "sum(increase(campaign_cache_value_size_bytes_sum{$JOB, state=\"compressed\"}[${win}s])) / sum(increase(campaign_cache_value_size_bytes_count{$JOB, state=\"compressed\"}[${win}s]))" "$win_end")
  cache_get_avg=$(promq "sum(increase(campaign_cache_get_seconds_sum{$JOB}[${win}s])) / sum(increase(campaign_cache_get_seconds_count{$JOB}[${win}s]))" "$win_end")

  docker compose cp backend:/logs/gc.log "$OUT_DIR/$tag.gc.log" >/dev/null 2>&1 || true

  echo "$RUN_SEQ,$name,$win_start_iso,$win_end_iso,$heap,$gc,$mem,$app_env,$win,$xmx,$count,$pause_sum,$pause_max,$alloc,$promoted,$heap_max,$overhead,$rps,$p95,$p99,$read_rps,$read_p95,$read_p99,$hikari_pending,$hikari_active,$cache_hit_rate,$cache_raw,$cache_compressed,$cache_get_avg" >>"$CSV"
  echo "  GC ${count%%.*}회 / 총정지 ${pause_sum}s / p99 ${p99}s"
}

mkdir -p "$OUT_DIR"
echo "seq,name,window_start,window_end,heap_opts,gc_opts,mem_limit,app_env,window_s,xmx_bytes,gc_count,pause_sum_s,pause_max_s,alloc_bytes,promoted_bytes,heap_used_max_bytes,gc_overhead,rps,p95_s,p99_s,read_rps,read_p95_s,read_p99_s,hikari_pending_max,hikari_active_max,cache_hit_rate,cache_raw_bytes,cache_compressed_bytes,cache_get_avg_s" >"$CSV"

# 인자를 주면 그 순서대로 돈다. 순서를 바꿔 두 번 돌려서 결과가 뒤집히면,
# 그건 설정 차이가 아니라 실행 순서(장비가 식거나 데이터가 쌓이는 것)를 본 것이다.
lookup() {
  local wanted=$1 entry name rest
  for entry in "${MATRIX[@]}"; do
    name=${entry%%|*}
    rest=${entry#*|}
    if [ "$name" = "$wanted" ]; then
      printf '%s' "$rest"
      return 0
    fi
  done
  return 1
}

if [ $# -gt 0 ]; then
  for wanted in "$@"; do
    rest=$(lookup "$wanted") || { echo "그런 설정이 없다: $wanted"; exit 1; }
    IFS='|' read -r heap gc mem app_env <<<"$rest"
    run_one "$wanted" "$heap" "$gc" "$mem" "$app_env"
  done
else
  for entry in "${MATRIX[@]}"; do
    IFS='|' read -r name heap gc mem app_env <<<"$entry"
    run_one "$name" "$heap" "$gc" "$mem" "$app_env"
  done
fi

echo
echo "결과: $OUT_DIR"
python3 - "$CSV" <<'PY'
import csv, sys

rows = list(csv.DictReader(open(sys.argv[1])))
if not rows:
    sys.exit("측정된 행이 없다")

import math

def f(v, d=0.0):
    """프로메테우스는 값이 없으면 빈 문자열, 0/0 이면 "NaN" 을 준다. 둘 다 없는 값이다."""
    try:
        x = float(v)
    except (TypeError, ValueError):
        return d
    return d if math.isnan(x) else x

mb = lambda v: f(v) / 1024 / 1024
ms = lambda v: f"{f(v) * 1000:.0f}"

def table(title, cols, out):
    w = [max(len(str(x)) for x in [c] + [r[i] for r in out]) for i, c in enumerate(cols)]
    line = lambda cells: "| " + " | ".join(str(c).ljust(w[i]) for i, c in enumerate(cells)) + " |"
    print()
    print(f"[{title}]")
    print(line(cols))
    print("|" + "|".join("-" * (x + 2) for x in w) + "|")
    for r in out:
        print(line(r))

hhmmss = lambda iso: iso[11:19] if len(iso) >= 19 else iso
print()
print("[측정 구간]")
for r in rows:
    print(f"  [{r['seq']}] {r['name']}: {hhmmss(r['window_start'])} ~ {hhmmss(r['window_end'])} ({r['window_s']}s)")

gc_rows, app_rows = [], []
for r in rows:
    n = f(r["gc_count"])
    # 처리량이 다르면 총량 비교는 의미가 없다. 요청 하나가 만든 쓰레기로 환산해야
    # "설정을 바꿔서 덜 할당하게 됐는지"를 볼 수 있다.
    reqs = f(r["rps"]) * f(r["window_s"])
    gc_rows.append([
        f"{r['seq']}. {r['name']}",
        f"{mb(r['xmx_bytes']):.0f}",
        f"{n:.0f}",
        f"{f(r['pause_sum_s']):.2f}",
        f"{f(r['pause_sum_s']) / n * 1000:.1f}" if n else "-",
        ms(r["pause_max_s"]),
        f"{mb(r['alloc_bytes']):.0f}",
        f"{mb(r['promoted_bytes']):.1f}",
        f"{mb(r['heap_used_max_bytes']):.0f}",
        f"{f(r['gc_overhead']) * 100:.2f}%",
        f"{f(r['alloc_bytes']) / reqs / 1024:.1f}" if reqs else "-",
    ])
    app_rows.append([
        f"{r['seq']}. {r['name']}",
        f"{f(r['rps']):.1f}",
        ms(r["p95_s"]),
        ms(r["p99_s"]),
        f"{f(r['read_rps']):.1f}",
        ms(r["read_p95_s"]),
        ms(r["read_p99_s"]),
        f"{f(r['hikari_active_max']):.0f}",
        f"{f(r['hikari_pending_max']):.0f}",
    ])

cache_rows = []
for r in rows:
    raw, comp = f(r["cache_raw_bytes"]), f(r["cache_compressed_bytes"])
    if not f(r["cache_get_avg_s"]) and not raw and not comp:
        continue  # 캐시를 끈 실행. 지표 자체가 없다
    # 크기는 캐시에 '쓸 때'만 기록된다. 히트율이 높으면 측정 구간 안에 쓰기가 없어서 비어 있는데,
    # 그건 값이 없는 것이지 0 이 아니다.
    cache_rows.append([
        f"{r['seq']}. {r['name']}",
        f"{f(r['cache_hit_rate']) * 100:.1f}%",
        f"{raw:.0f}" if raw else "쓰기 없음",
        f"{comp:.0f}" if comp else "쓰기 없음",
        f"{raw / comp:.1f}x" if raw and comp else "-",
        f"{f(r['cache_get_avg_s']) * 1000:.2f}",
    ])

table("GC / 메모리", ["설정", "Xmx(MB)", "GC 횟수", "총 정지(s)", "평균(ms)", "최대(ms)", "할당(MB)", "승격(MB)", "힙피크(MB)", "오버헤드", "할당/요청(KB)"], gc_rows)
table("응답 / DB", ["설정", "rps", "p95(ms)", "p99(ms)", "조회 rps", "조회 p95", "조회 p99", "Hikari 사용", "Hikari 대기"], app_rows)
if cache_rows:
    table("캐시", ["설정", "히트율", "원본(B)", "압축(B)", "압축률", "캐시조회(ms)"], cache_rows)
PY

echo
echo "원래 설정으로 되돌리려면:"
echo "  docker compose up -d --force-recreate backend"
