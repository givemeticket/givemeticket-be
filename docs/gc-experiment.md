# GC / 힙 크기 실험

부하를 주면서 GC가 언제 얼마나 도는지 보고, 힙 크기와 GC 종류를 바꿔 가며 비교하는 방법.

## 무엇을 보게 되나

Micrometer가 이미 `jvm_gc_*` 지표를 프로메테우스로 뱉고 있었지만 대시보드에는 힙 사용량 한 줄만
있었다. **GiveMeTicket — JVM/GC 메모리** 대시보드(`givemeticket-jvm`)에 GC 지표를 모아 두었다.

```bash
docker compose --profile obs up -d
```

Grafana `http://localhost:3001` → 대시보드 목록 → *GiveMeTicket — JVM/GC 메모리*.

| 패널 | 지표 | 이걸로 판단하는 것 |
| --- | --- | --- |
| 힙 사용량 | `jvm_memory_used_bytes{area="heap"}` | 톱니의 진폭 = young GC 한 번이 회수하는 양. 톱니의 **바닥선**이 계속 올라가면 old에 쌓이는 중 |
| 힙 영역별 | 같은 지표를 `id` 별로 | Eden이 차오르는 기울기가 곧 할당 속도. Old가 한 번에 뚝 떨어지면 mixed/full GC |
| GC 발생 횟수 | `rate(jvm_gc_pause_seconds_count[1m])` | `action`(end of minor/major)과 `cause`를 나눠서 본다 |
| GC 정지 시간 | `rate(jvm_gc_pause_seconds_sum[1m])` | 1분당 멈춰 있던 시간. 0.6이면 그 1분의 1% |
| 한 번의 정지 | `jvm_gc_pause_seconds_max`, sum/count | **횟수와 세트로** 봐야 트레이드오프가 보인다 |
| 할당률/승격률 | `jvm_gc_memory_allocated_bytes_total`, `..._promoted_...` | 승격률이 꾸준히 0보다 크면 객체가 GC 주기보다 오래 산다는 뜻 |
| live data size | `jvm_gc_live_data_size_bytes` | 부하가 끝나고도 안 내려가면 누수 |
| 힙 밖 메모리 | `jvm_memory_used_bytes{area="nonheap"}` | 힙만 키우다 OOMKilled 날 때 여기를 본다 |
| GC 로그 | Loki `{service="givemeticket-jvm"}` | 지표에서 튄 순간의 원문 한 줄을 바로 확인 |

지연 p95/p99와 처리량 패널을 아래에 같이 놓았다. **GC 정지가 튄 순간 p99가 같이 튀는지**를 보는
것이 이 대시보드의 목적이다. GC가 잦아도 지연이 안 흔들리면 그건 튜닝할 문제가 아니다.

## GC 로그 원문

지표는 5초에 한 번 긁은 요약이라 개별 GC 한 건은 로그로 본다.

```bash
docker compose exec backend tail -f /logs/gc.log
```

```
[2026-08-14T11:38:39.915+0000][0.883s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 35M->3M(718M) 15.136ms
```

`35M->3M(718M)`은 GC 전 35M → GC 후 3M, 그 시점의 커밋된 힙이 718M, 정지는 15ms였다는 뜻이다.
화살표 오른쪽 값(GC 후)이 실행할수록 커지면 그게 누수 신호다.

힙 영역별 증감과 pause 단계까지 필요하면 `-Xlog:gc*`로 남기는 상세 로그를 본다.

```bash
docker compose exec backend tail -f /logs/gc-detail.log
```

## 설정 바꾸기

힙과 GC를 환경변수로 빼 놨다. 이미지를 다시 굽지 않고 컨테이너만 다시 띄우면 된다.

```bash
JVM_HEAP_OPTS="-Xms256m -Xmx256m" docker compose up -d --force-recreate backend
```

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `JVM_HEAP_OPTS` | `-XX:InitialRAMPercentage=70.0 -XX:MaxRAMPercentage=70.0` | 힙 크기 |
| `JVM_GC_OPTS` | `-XX:+UseG1GC` | GC 종류 |
| `JAVA_OPTS_EXTRA` | (없음) | `-XX:MaxGCPauseMillis=100` 같은 튜닝 플래그를 하나씩 |
| `BACKEND_MEM_LIMIT` | `1g` | 컨테이너 메모리 |
| `BACKEND_CPUS` | `1.0` | 컨테이너 CPU |

적용됐는지 확인:

```bash
docker compose exec backend sh -c 'jcmd 1 VM.flags' | tr ' ' '\n' | grep -E 'MaxHeapSize|Use.*GC'
```

알아 둘 것 두 가지.

- **컨테이너 메모리를 줄이면 힙도 같이 줄어든다.** 기본값이 `MaxRAMPercentage=70`이라
  1g → 힙 700M, 512m → 힙 358M이다. 힙만 따로 비교하고 싶으면 컨테이너 메모리는 고정하고
  `-Xmx`를 고정값으로 준다.
- **CPU를 1개로 묶어 두면 GC 워커 스레드도 1개다.** `-XX:+UseG1GC`를 명시하지 않으면 JVM은
  CPU 1개짜리 컨테이너에서 **Serial GC**를 고른다. GC를 비교할 때 CPU 수를 함께 적어 두지 않으면
  나중에 그 숫자를 해석할 수 없다.

## 매트릭스 자동 실행

설정을 바꿔 재시작 → 부하 → 지표 수집을 반복하는 스크립트다.

```bash
docker compose --profile obs up -d
./load-test/gc-matrix.sh
```

기본 4개 조합(G1 256m / 512m / 700m, Parallel 256m)을 각각 돌리고 표를 찍는다.

```
| 설정     | Xmx(MB) | GC 횟수 | 총 정지(s) | 평균(ms) | 최대(ms) | 할당(MB) | 승격(MB) | 힙피크(MB) | GC오버헤드 | rps  | p95(ms) | p99(ms) |
```

- 이름을 인자로 주면 그것만 돈다: `./load-test/gc-matrix.sh g1-256m par-256m`
- 부하 조절: `DURATION=3m VUS=80 ./load-test/gc-matrix.sh`
- 조합을 바꾸려면 스크립트 위쪽 `MATRIX` 배열을 고친다
- 결과는 `load-test/results/gc-<timestamp>/`에 CSV, k6 출력, 실행별 `gc.log`로 남는다

측정 구간은 soak의 램프업 30초와 램프다운 20초를 잘라낸 정상 부하 구간이다. 매 실행 전
컨테이너를 새로 띄우고 10초 예열하기 때문에 JIT 상태도 대충 맞춰진다.

## 해석할 때 주의할 것

**먼저 부하가 실제로 들어갔는지 본다.** k6는 서버와 같은 시크릿으로 액세스 토큰을 직접 서명한다
([load-test/auth.js](../load-test/auth.js)). 시크릿이 어긋나면 전 요청이 401로 떨어지는데,
그래도 표는 채워진다 — 401도 요청이라 rps는 잡히고 GC는 거의 안 돈다. k6 출력의
`checks_succeeded`가 0%면 그 표는 버린다.

**처리량이 같아야 비교가 된다.** 힙을 줄였더니 GC 횟수가 늘었는데 rps도 같이 떨어졌다면, GC가
느려서인지 부하가 덜 들어가서인지 구분되지 않는다. 표의 `rps`와 `할당(MB)`이 조합끼리 비슷한지
먼저 확인하고 나머지 숫자를 읽는다.

**힙을 키우면 GC 횟수는 줄고 한 번의 정지는 길어진다.** 어느 쪽이 이득인지는 총 정지 시간이 아니라
p99가 결정한다. 처리량이 중요하면 총 정지, 응답 지연이 중요하면 최대 정지를 본다.

**"GC가 안 일어났다"는 대개 힙이 커서가 아니라 할당이 줄어서다.** 캐시를 넣었더니 GC가 사라졌다면
힙 크기가 아니라 **할당률** 패널이 근거다. 요청당 만들어 내던 객체가 줄어든 것이지 GC가 좋아진 게
아니다. 이 구분을 표에서는 `할당(MB)` 열이 해 준다.

**Full GC가 안 보이는 게 정상이다.** G1에서 `action="end of major GC"`가 뜨거나 GC 로그에
`Pause Full`이 찍히면 그건 이미 실패에 가깝다. 힙을 늘리거나 할당을 줄여야 한다.

## 더 해 볼 만한 실험

| 바꾸는 것 | 명령 | 볼 것 |
| --- | --- | --- |
| 힙을 극단적으로 작게 | `JVM_HEAP_OPTS="-Xms128m -Xmx128m"` | full GC와 `OutOfMemoryError`, `/logs/dumps` 힙덤프 |
| GC 교체 | `JVM_GC_OPTS="-XX:+UseParallelGC"` | 정지는 길지만 처리량이 오르는지 |
| 저지연 GC | `JVM_GC_OPTS="-XX:+UseZGC -XX:+ZGenerational"` `BACKEND_CPUS=2` | 최대 정지가 ms 이하로 떨어지는 대신 CPU와 메모리를 얼마나 더 쓰는지 |
| 목표 정지 시간 | `JAVA_OPTS_EXTRA="-XX:MaxGCPauseMillis=50"` | G1이 young 영역을 줄여 목표를 맞추는지, 그래서 횟수가 느는지 |
| CPU 수 | `BACKEND_CPUS=2` | GC 워커가 늘어 정지가 짧아지는지 |
| 부하 모양 | `SCRIPT=load-test/rush.js ./load-test/gc-matrix.sh` | 스파이크에서 할당이 순간적으로 튈 때 |

ZGC는 컨테이너 CPU가 1개면 오히려 손해다. 저지연 GC를 볼 때는 `BACKEND_CPUS`를 같이 올린다.

## 프로덕션에는 그대로 옮기지 않는다

`docker/docker-compose.prod.yml`의 backend에는 이 변수들을 넣지 않았다. 실험으로 값을 정한
다음에 그때 명시적으로 넣는다. GC 로그는 프로덕션에서도 그대로 남는다.
