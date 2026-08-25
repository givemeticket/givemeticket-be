# 상황 설명
현재 나는 선착순 서비스를 만들고 있다. 선착순 서비스를 보통 생각해보면 예매 직전 사용자들이 엄청나게 몰리게 된다. 실제 수강신청을 했던 내 모습을 보더라도 새로고침을 반복해서 요청하는 과정에서 조회 요청을 여러번 누르고 있다.

그럼 만약 DB 조회 요청이 반복되면? 1인당 3번 x 100명 x 10개의 행사 일때 순간적인 DB 조회 요청이 3000번 요청된다. 주 기능인 선착순 신청이 시작하기도 전에 이미 DB 커넥션이 엄청나게 소요 되는 것이다.

DB 인덱싱을 통해서 DB 조회 시간을 단축시켰다고 해서 커넥션의 기본적인 요청량은 줄여지지 않는다. 그럼 해결책은 캐싱으로 이어지게 된다.
![[Pasted image 20260816163627.png]]

현재 보이는 것으로는 HikariCP 커넥션에서 대기중인 커넥션으로 인해서 병목이 발생하고 있다.

![[Pasted image 20260816163546.png]]


# 캐싱 기법
캐싱을 사용한다는 것은 주로 변하지 않고 자주 요청되는 데이터를 위주로 캐싱을 한다고 한다.
그럼 행사 정보 조회같은 여러 사람들이 사용하는 변하지 않는 데이터를 캐싱해두면 되지 않을까? 캐싱을 위해서는 크게 2가지 방법이 있다.
1. 인메모리 캐싱
2. Redis 캐싱

먼저 인메모리 캐싱과 Redis 캐싱이 있다. 근데 만약 순수하게 캐싱 목적만을 생각해서는 Redis 캐싱이 더 좋다고 생각할 수 있다. 다중 서버 환경에서 Redis라는 중앙 저장소에 캐싱을 해두면 캐싱 메모리를 중앙에서 관리할 수 있다는 큰 장점이 생긴다.

인메모리에서 캐싱을 사용할 경우 배포 과정에서 메모리가 비어지게 되거나, 캐시 메모리의 생명주기가 서버의 생명주기에 의존하게 되는 것도 문제라고 생각할 수 있다.

이런 생각들을 바탕으로 Redis를 캐싱 목적으로 먼저 사용하게 되었다.


# Redis 캐싱
먼저 Redis에 저장할 데이터는 선착순 대상인 행사에 관한 정보들을 캐싱할 예정이다.

Redis 캐싱시 고려할 것이 2가지 있다.
1. 행사가 종료된 데이터에 관해서는 캐싱하지 않음
2. 행사 관련 데이터를 저장할때 최대한 압축해서 데이터를 저장함.

즉 메모리는 크지 않고 데이터 왕복에서 비용이 발생하기 때문에 데이터를 최소한으로 캐싱하는 것이 중요하다고 생각하여 위에 2가지를 적용하였다.

## 캐시에 담을 값 정하기

본격적으로 들어가기 전에 무엇을 담을지부터 정해야 했다. 엔티티를 그대로 직렬화하는 방법도 있었지만 두 가지가 걸렸다. 캐시에서 꺼낸 객체가 영속 상태로 오해받을 수 있고, 캐시에 담긴 포맷이 JPA 매핑 변경에 끌려다니게 된다. 그래서 값 스냅샷을 따로 만들었다.

```java
public record CampaignSnapshot(
        Long id,
        Long ownerId,
        String shortCode,
        String title,
        CampaignType type,
        int totalStock,
        LocalDateTime openAt,
        boolean requiresPayment,
        CampaignStatus status,
        Detail detail
) {
    public static CampaignSnapshot from(Campaign campaign) { ... }
}
```

여기서 중요한 건 **담지 않은 것**이다. 잔여 재고를 넣지 않았다. 재고는 Redis 카운터가 진실이고 요청마다 달라지는 값이라, 캐시에 넣는 순간 캐시가 매진을 숨기게 된다. 선착순 서비스에서 이건 치명적이다. 그래서 재고는 항상 밖에서 따로 읽어 응답을 만든다.

## 행사 전 데이터들만 캐싱

끝난 행사는 다시 몰려서 조회될 일이 없는데 캐시 메모리만 차지한다. 그래서 캐싱 대상에서 빼기로 했다.

처음에는 단순하게 상태값만 보면 된다고 생각했다. `CampaignStatus`에 `CLOSED`가 있으니 그걸 쓰면 되겠다고.

그런데 코드를 뒤져보니 **`Campaign.close()`를 호출하는 곳이 어디에도 없었다.** 메서드는 정의돼 있는데 부르는 데가 없으니 `CLOSED` 상태로 가는 경로가 실제로는 존재하지 않는 것이다. 이 상태로 상태값만 보고 판단하면 이 규칙은 그냥 죽은 코드가 된다.

그래서 상태와 함께 행사 종료 시각도 같이 보도록 했다. 나중에 `close()` 전이가 생겨도 그대로 동작한다.

```java
/**
 * 캐시에 올릴 값인지. 끝난 행사는 다시 몰려서 조회될 일이 없는데 캐시(=메모리)만 차지한다.
 */
public boolean isCacheable(LocalDateTime now) {
    if (status == CampaignStatus.DELETED || status == CampaignStatus.CLOSED) {
        return false;
    }
    return detail == null
            || detail.eventEndAt() == null
            || !detail.eventEndAt().isBefore(now);
}
```

실제로 캐시를 채우는 지점에서 이 조건을 본다.

```java
private CampaignSnapshot findCachedCampaign(String shortCode) {
    Optional<CampaignSnapshot> cached = campaignCacheRepository.find(shortCode);
    if (cached.isPresent()) {
        return cached.get();
    }

    Campaign campaign = campaignRepository.findByShortCode(shortCode)
            .orElseThrow(CampaignApplicationException::campaignNotFound);

    CampaignSnapshot snapshot = CampaignSnapshot.from(campaign);
    if (snapshot.isCacheable(LocalDateTime.now())) {
        campaignCacheRepository.save(snapshot);
    }
    return snapshot;
}
```

## 행사 데이터 저장시 압축하여 데이터 캐싱

압축 라이브러리를 고르면서 후보를 몇 개 놓고 봤다.

| 후보 | 장점 | 단점 |
| --- | --- | --- |
| **gzip (JDK 내장)** | 의존성 0, 압축률 좋음 | 압축 속도가 상대적으로 느림 |
| Snappy / LZ4 | 압축·해제가 매우 빠름 | 압축률이 낮음, 별도 의존성 |
| Zstd | 압축률·속도 둘 다 좋음 | 네이티브 라이브러리 의존 |

결론은 **JDK에 내장된 gzip(`java.util.zip`)** 이었다. 이유는 두 가지다.

첫째, 의존성이 하나도 늘지 않는다. Snappy나 Zstd는 성능은 좋지만 라이브러리를 추가해야 하고, Zstd는 네이티브 바이너리까지 딸려온다. 지금 단계에서 그만한 이득이 있는지 확신할 수 없었다.

둘째, 우리 데이터와 궁합이 맞다. 행사 안내문은 "공연 30분 전부터 입장 가능합니다" 같은 문장이 반복되는 글이다. 이런 반복이 많은 텍스트에서 gzip은 압축률이 잘 나온다. 반대로 Snappy 계열은 속도를 위해 압축률을 포기하는 방식이라, 네트워크 왕복 바이트를 줄이려는 목적에는 덜 맞다.

실제로 재보니 **3,000자짜리 안내문이 7,497바이트에서 561바이트로 줄었다. 13.4배다.**

압축이 공짜는 아니라는 점도 짚고 넘어가야 한다. 압축과 해제가 요청마다 CPU를 쓰고 중간 버퍼를 힙에 만든다. 값이 작으면 gzip 헤더(약 20바이트)와 CPU 값만 치르고 얻는 게 없을 수도 있다. 그래서 **원본과 압축 후 크기를 둘 다 지표로 남기도록** 했다. 실제로 줄었는지 보고 판단하기 위해서다.

```java
@Override
public byte[] serialize(T value) throws SerializationException {
    if (value == null) {
        return EMPTY;
    }
    try {
        byte[] json = objectMapper.writeValueAsBytes(value);
        byte[] compressed = gzip(json);

        rawSize.record(json.length);          // campaign_cache_value_size_bytes{state="raw"}
        compressedSize.record(compressed.length);  // ...{state="compressed"}

        return compressed;
    } catch (IOException e) {
        throw new SerializationException("캐시 값을 압축하지 못했다", e);
    }
}

@Override
public T deserialize(byte[] bytes) throws SerializationException {
    if (bytes == null || bytes.length == 0) {
        return null;
    }
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
        return objectMapper.readValue(in.readAllBytes(), type);
    } catch (IOException e) {
        // 포맷이 바뀌었거나 값이 깨진 경우다. 캐시 미스로 떨어뜨려 DB 에서 다시 읽게 한다.
        throw new SerializationException("캐시 값을 풀지 못했다", e);
    }
}

private byte[] gzip(byte[] raw) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(raw.length / 2);
    try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
        gzip.write(raw);
    }
    return buffer.toByteArray();
}
```

### 여기서 한 번 데였다

테스트를 짜다가 왕복이 깨지는 걸 발견했다. 직렬화는 되는데 역직렬화에서 터졌다.

```
UnrecognizedPropertyException: Unrecognized field "deleted"
(class CampaignSnapshot), not marked as ignorable
```

원인은 `CampaignSnapshot`에 넣어둔 `isDeleted()` 메서드였다. Jackson이 이걸 boolean getter로 인식해서 `deleted`라는 필드를 JSON에 같이 써버린 것이다. 읽을 때는 대응하는 레코드 컴포넌트가 없으니 깨진다. **압축 바이트를 줄이겠다고 하면서 정작 쓸데없는 필드를 같이 쓰고 있었던 셈이다.**

그래서 캐시 전용 ObjectMapper를 따로 만들고 is-getter 탐지를 껐다.

```java
public static ObjectMapper defaultObjectMapper() {
    return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // isDeleted() 같은 파생 메서드가 필드로 새어 나가면 바이트가 늘고 역직렬화가 깨진다
            .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
            // 스냅샷에 필드를 추가한 배포 직후, Redis 에 남아 있는 옛 값 때문에 전부 터지는 것보다
            // 조용히 무시하고 TTL 로 갈리는 편이 낫다
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
}
```

애플리케이션 공용 ObjectMapper를 가져다 쓰지 않고 여기서 따로 만든 것도 의도한 것이다. 응답 JSON 설정을 바꿨다고 Redis에 이미 쌓여 있는 값의 포맷이 조용히 달라지면 곤란하다.

이 직렬화기를 캠페인 캐시 전용 `RedisTemplate`에 물렸다.

```java
@Bean
public RedisTemplate<String, CampaignSnapshot> campaignCacheRedisTemplate(
        RedisConnectionFactory connectionFactory,
        MeterRegistry meterRegistry
) {
    RedisTemplate<String, CampaignSnapshot> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GzipRedisSerializer<>(
            CampaignSnapshot.class,
            GzipRedisSerializer.defaultObjectMapper(),
            meterRegistry,
            "campaign"));
    return template;
}
```


# Redis를 사용해서 캐싱
앞선 결과 Redis를 사용해서 캐싱을 진행해보았다.
먼저 기존 데이터를 압축하여 redis에 저장하고 압축을 풀어서 꺼내는 부분이다.

키는 `campaign:detail:{shortCode}` 형태로 잡았다. 조회 API가 shortCode로 들어오기 때문에 그대로 키로 쓰는 게 자연스러웠다.

```java
private static final String KEY_PREFIX = "campaign:detail:";

@Override
public Optional<CampaignSnapshot> find(String shortCode) {
    try {
        CampaignSnapshot cached = getTimer.record(() -> redisTemplate.opsForValue().get(key(shortCode)));

        if (cached == null) {
            misses.increment();
            return Optional.empty();
        }
        hits.increment();
        return Optional.of(cached);
    } catch (RuntimeException e) {
        // 값이 깨졌으면 그대로 두면 계속 실패한다. 지우고 DB 에서 다시 채우게 한다.
        errors.increment();
        log.warn("campaign cache read failed: shortCode={}, reason={}", shortCode, e.toString());
        evict(shortCode);
        return Optional.empty();
    }
}

@Override
public void save(CampaignSnapshot snapshot) {
    try {
        redisTemplate.opsForValue().set(key(snapshot.shortCode()), snapshot, ttl);
    } catch (RuntimeException e) {
        errors.increment();
        log.warn("campaign cache write failed: shortCode={}, reason={}", snapshot.shortCode(), e.toString());
    }
}

@Override
public void evict(String shortCode) {
    try {
        redisTemplate.delete(key(shortCode));
    } catch (RuntimeException e) {
        // 지우지 못한 캐시는 TTL 이 만료될 때까지 낡은 값을 준다. 조용히 넘기면 안 된다.
        errors.increment();
        log.error("campaign cache evict failed: shortCode={}", shortCode, e);
    }
}
```

여기서 신경 쓴 것은 **캐시는 거들 뿐이라는 원칙**이다. Redis가 죽거나 값이 깨져도 조회 자체는 성공해야 한다. 그래서 이 클래스의 모든 실패는 로그와 지표로만 남기고 캐시 미스처럼 굴게 했다. 캐시를 넣었더니 장애 포인트가 하나 늘어나는 상황은 피하고 싶었다.

## 캐시를 언제 지울 것인가

넣는 것보다 지우는 게 어려웠다. 무효화 지점을 찾아보니 세 곳이었다.

1. 행사 수정 (정원 증원, 안내문 변경)
2. 행사 삭제
3. **오픈 스케줄러**

3번을 놓칠 뻔했다. 이 서비스는 `status`가 DB 컬럼이고 스케줄러가 1초마다 `SCHEDULED → OPEN`으로 바꾼다. 그런데 오픈 직전이야말로 조회가 가장 몰리는 구간이다. 캐시가 `SCHEDULED`로 굳어 있으면 **열린 행사를 닫혀 있다고 보여주게 된다.** 선착순 서비스에서 이건 그냥 장애다.

```java
for (Campaign campaign : targets) {
    campaign.open();
    campaignStateRepository.save(campaign.getId(),
            new CampaignState(campaign.isRequiresPayment(), campaign.getTotalStock()));
    // 오픈 직전은 조회가 가장 몰리는 구간이다. 캐시가 SCHEDULED 로 굳어 있으면
    // 열린 행사를 닫혀 있다고 보여 준다.
    campaignCacheEvictor.evict(campaign.getShortCode());
}
```

그리고 지우는 것도 한 번으로는 부족했다.

```java
public void evict(String shortCode) {
    campaignCacheRepository.evict(shortCode);

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
            campaignCacheRepository.evict(shortCode);
        }
    });
}
```

**두 번 지우는 이유**는 이렇다. 지금 한 번만 지우면, 커밋이 끝나기 전에 들어온 조회가 **바뀌기 전 값**을 읽어서 캐시를 다시 채워버린다. 그리고 그 낡은 값이 TTL이 만료될 때까지 남는다. 커밋 뒤에 한 번 더 지워야 그 창이 닫힌다.

롤백된 경우에도 지운다. 지울 필요 없는 캐시를 지운 대가는 캐시 미스 한 번뿐이라, 낡은 값을 남기는 쪽보다 항상 싸다.

무효화만 믿지 않고 TTL도 같이 뒀다. 어떤 이유로든 무효화를 놓쳤을 때 낡은 값이 영원히 남지 않게 하는 보험이다.

```yaml
campaign:
  cache:
    mode: ${CAMPAIGN_CACHE_MODE:redis}   # none | redis | local
    ttl: ${CAMPAIGN_CACHE_TTL:10m}
```

## 진짜 압축돼서 들어갔나

지표만 믿기가 좀 그래서 Redis에 실제로 들어간 바이트를 직접 열어봤다.

```bash
$ redis-cli --scan --pattern 'campaign:detail:*' | head -1
campaign:detail:hZQMNbqoyO

$ redis-cli STRLEN campaign:detail:hZQMNbqoyO
561

$ redis-cli --no-raw GETRANGE campaign:detail:hZQMNbqoyO 0 1
"\x1f\x8b"
```

`1f 8b`는 gzip 매직넘버다. 압축을 풀어보니 7,497바이트가 나왔다. 의도대로 들어가 있다.


# Redis에서 사용할때 문제는 없을까?
Redis를 사용할 때 가장 큰 문제는 결국 Redis에도 부하가 그대로 옮겨간다는 것이다. DB 커넥션 대기는 사라졌지만, **조회 한 번마다 네트워크 왕복 + 압축 해제 + JSON 파싱이 여전히 남는다.**

그래서 "그럼 Redis 조회가 DB 조회보다 무거운가?"가 궁금해졌고, 재봤더니 예상과 달랐다.

| | 요청당 할당 |
| --- | --- |
| DB 직접 조회 | 765KB |
| Redis 캐시 조회 | 570KB |

**Redis 쪽이 오히려 적게 할당했다.** 압축을 하기 때문이다. DB는 데이터를 압축 없이 그대로 네트워크로 보내고, JDBC가 그걸 String으로 만들고, Hibernate가 엔티티를 만들고, 영속성 컨텍스트에 등록까지 한다. Redis는 압축된 몇 KB만 받아서 풀면 된다. gzip 해제 비용을 물어도 그게 더 싸다.

그렇다고 Redis 조회가 공짜라는 뜻은 아니다. 부하를 한계까지 밀어보면 드러난다.

| | 1600 rps 목표 달성률 | CPU |
| --- | --- | --- |
| DB 직접 조회 | 58% (927 rps) | 100% |
| Redis 캐시 | 74% (1,176 rps) | 95% |

Redis가 DB보다 1.27배 더 버티긴 했지만, **결국 같이 무너졌다.** 조회당 570KB를 할당하니 1,600 rps면 초당 890MB를 할당해야 하는데 그게 감당이 안 됐다.

즉 Redis 캐싱은 DB 커넥션 병목은 확실히 없애주지만, **조회당 비용을 0으로 만들어주지는 않는다.** 그 비용을 더 줄이려면 네트워크와 역직렬화 자체를 없애야 한다. 그래서 로컬 캐시로 넘어갔다.


# Local Cache 로 캐싱 진행
로컬 캐싱을 진행하기 위해서 Caffeine을 선택하였다.
로컬 캐시를 위해서는 크게 4가지 선택지가 있었다.

| 후보 | 평가 |
| --- | --- |
| `ConcurrentHashMap` 직접 구현 | 의존성은 없지만 TTL, 최대 크기, 축출 정책을 전부 직접 만들어야 한다. 캐시가 무한정 커져서 힙을 먹는 사고가 나기 쉽다 |
| Guava Cache | 검증됐지만 사실상 유지보수가 멈췄고, 개발자 본인이 Caffeine을 후계로 만들었다 |
| Ehcache | 디스크 저장, 분산 캐시까지 되는 대신 무겁다. 프로세스 안 캐시만 필요한 지금은 과하다 |
| **Caffeine** | Guava Cache의 후계. W-TinyLFU 축출 정책으로 히트율이 좋고, Spring Boot가 기본 지원한다 |

`ConcurrentHashMap`을 직접 쓰지 않은 이유가 특히 중요하다. **로컬 캐시는 힙에 그대로 얹힌다.** 캠페인이 늘어나면 old 영역에 상주하는 양이 늘고, 그건 GC에 그대로 나타난다. 최대 크기를 강제하는 장치가 없으면 캐시를 넣어서 GC를 줄이려다 오히려 GC를 늘리게 된다.

```java
public CampaignLocalCache(Duration ttl, long maxSize, MeterRegistry meterRegistry) {
    this.cache = Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize)
            .build();
    this.hits = counter(meterRegistry, "hit");
    this.misses = counter(meterRegistry, "miss");

    // 힙에 상주하는 양을 가늠하려고 항목 수를 지표로 뽑는다
    Gauge.builder("campaign.local.cache.entries", cache, Cache::estimatedSize)
            .register(meterRegistry);
}

public Optional<CampaignSnapshot> find(String shortCode) {
    CampaignSnapshot cached = cache.getIfPresent(shortCode);
    if (cached == null) {
        misses.increment();
        return Optional.empty();
    }
    hits.increment();
    return Optional.of(cached);
}

public void put(CampaignSnapshot snapshot) {
    cache.put(snapshot.shortCode(), snapshot);
}

public void evict(String shortCode) {
    cache.invalidate(shortCode);
}
```

## 로컬 캐시와 Redis를 2단으로

로컬 캐시를 넣는다고 Redis를 버리는 게 아니다. 로컬을 앞(L1), Redis를 뒤(L2)에 두는 2단 구성으로 갔다.

```java
@Override
public Optional<CampaignSnapshot> find(String shortCode) {
    Optional<CampaignSnapshot> local = localCache.find(shortCode);
    if (local.isPresent()) {
        return local;
    }

    Optional<CampaignSnapshot> remote = remoteCache.find(shortCode);
    remote.ifPresent(localCache::put);   // Redis 에서 찾으면 로컬에도 올린다
    return remote;
}

@Override
public void evict(String shortCode) {
    localCache.evict(shortCode);    // 가까운 곳부터
    remoteCache.evict(shortCode);
    invalidation.publish(shortCode);  // 다른 인스턴스에게 알린다
}
```

조회는 L1 → L2 순인데, **무효화는 반대로 가까운 곳부터 지운다.** L2를 먼저 지우면 그 사이 L1이 살아 있어서 낡은 값을 계속 주기 때문이다.

## 인메모리 캐싱의 그 문제, pub/sub으로 풀기

글 앞에서 인메모리 캐싱의 단점으로 "다중 서버 환경에서 캐시를 중앙 관리할 수 없다"를 들었다. 이제 그 문제를 정면으로 마주하게 됐다. 로컬 캐시는 인스턴스마다 따로 산다. 한 서버에서 행사를 수정해도 다른 서버는 낡은 값을 계속 준다.

Redis pub/sub으로 "이 shortCode를 버려라"를 방송하는 방식으로 풀었다.

```java
public static final String CHANNEL = "campaign:cache:invalidate";
/** 인스턴스마다 다른 값. 자기 방송을 걸러내는 데만 쓴다. */
private final String instanceId = UUID.randomUUID().toString();

public void publish(String shortCode) {
    try {
        stringRedisTemplate.convertAndSend(CHANNEL, instanceId + SEPARATOR + shortCode);
        published.increment();
    } catch (RuntimeException e) {
        // 방송이 실패해도 이 인스턴스의 캐시는 이미 지웠다. 다른 인스턴스는 TTL 로 갈린다.
        log.warn("cache invalidation publish failed: shortCode={}, reason={}", shortCode, e.toString());
    }
}

@Override
public void onMessage(Message message, byte[] pattern) {
    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    int separator = body.indexOf(SEPARATOR);
    if (separator < 0) {
        log.warn("cache invalidation message malformed: {}", body);
        return;
    }

    String sender = body.substring(0, separator);
    String shortCode = body.substring(separator + 1);

    if (instanceId.equals(sender)) {
        return;   // 자기가 쏜 방송은 무시한다. 이미 지웠다
    }

    localCache.evict(shortCode);
    received.increment();
}
```

메시지에 보낸이를 넣은 이유는 자기가 쏜 방송을 되받아 이미 지운 것을 또 지우지 않기 위해서다. 해로울 건 없지만 지표가 부풀어서 히트율 해석이 흐려진다.

여기서 반드시 알아둬야 할 게 있다. **Redis pub/sub은 at-most-once다.** 구독이 끊긴 사이에 발행된 메시지는 다시 오지 않는다. 그래서 이것만 믿으면 안 되고, 로컬 캐시의 짧은 TTL(기본 30초)이 반드시 함께 있어야 한다. 방송은 정합성을 보장하는 장치가 아니라 **수렴을 앞당기는 최적화**로 봐야 한다.

실제로 인스턴스를 두 개 띄워서 확인해봤다. 인스턴스1에서 행사를 수정하고 인스턴스2에서 조회했더니 바로 새 값이 나왔다. 방송이 건너간 것이다.

```
생성: 201 shortCode=XUY6PfjnUL
1) 인스턴스1 조회: 200 "변경 전 제목"
2) 인스턴스2 조회: 200 "변경 전 제목"   <- 인스턴스2의 로컬 캐시에 올라감
3) 인스턴스1 에서 수정: 200
4) 인스턴스2 재조회 location = "고척돔"
   => 방송이 건너갔다 (pub/sub 동작)
```

## 세 가지를 갈아 끼울 수 있게

비교 실험을 하려면 같은 빌드에서 구성을 바꿀 수 있어야 했다. 조건부 애노테이션을 구현체마다 흩뿌리지 않고 설정 한 곳에서 조립하도록 했다.

```java
@Bean
public CampaignCacheRepository campaignCacheRepository(...) {
    Mode mode = Mode.from(modeValue);
    log.info("campaign cache mode: {}", mode);

    if (mode == Mode.NONE) {
        return new NoOpCampaignCacheRepository();
    }

    RedisCampaignCacheRepository remote =
            new RedisCampaignCacheRepository(campaignCacheRedisTemplate, meterRegistry, ttl);
    if (mode == Mode.REDIS) {
        return remote;
    }

    return new TieredCampaignCacheRepository(
            localCaches.getObject(), remote, invalidations.getObject());
}
```

`CAMPAIGN_CACHE_MODE` 환경변수 하나로 `none` / `redis` / `local`을 갈아 끼운다. 캐시를 끄면 `NoOp` 구현이 들어가서 Redis를 아예 부르지 않기 때문에, 기준선 측정에 캐시가 섞이지 않는다.


# 세 가지 방식의 데이터 조회 비교
데이터를 조회하는 과정에서 3가지 방식을 비교해보았다. 이때 작은 데이터의 크기를 확인하기보다 더 큰 데이터를 가정하고 테스트를 진행해보았다.

## 실험 조건

작은 데이터로 먼저 재봤을 때는 **세 방식의 차이가 아예 측정되지 않았다.** 안내문 9KB 기준으로 캐시가 만지는 부분이 요청당 할당의 2%밖에 안 됐고, 그건 실행 간 잡음(±3.5%)보다 작았다. 기대 효과가 잡음보다 작으면 측정 도구가 그 차이를 분해할 수 없다.

그래서 조건을 바꿨다.

- 안내문 50,000자 (UTF-8 약 150KB)
- 캠페인 50개를 무작위로 조회
- **조회만** 하는 시나리오 (신청·확정 제외)
- **비로그인** 조회 — 로그인하면 내 신청 내역 조회가 매번 DB로 나가는데, 사용자별이라 캐시가 안 된다. 비로그인이면 캐시 히트 시 DB 쿼리가 0건이 된다
- 백엔드 2코어, 힙 718MB, G1GC
- 도착률을 100 → 200 → 400 → 800 → 1600 rps로 올리며 각 60초 유지

## 성능 비교

| 모드 | 목표 rps | 실제 rps | 달성률 | 할당/req | GC | 정지 | p99 | CPU |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| none | 100 | 100 | 100% | 765KB | 29회 | 0.18s | 74ms | 20% |
| none | 400 | 400 | 100% | 770KB | 115회 | 0.19s | 4ms | 15% |
| none | 800 | 800 | 100% | 781KB | 130회 | 0.45s | 15ms | 30% |
| none | 1600 | **927** | **58%** | 777KB | 114회 | **1.87s** | **998ms** | 100% |
| redis | 100 | 100 | 100% | 597KB | 23회 | 0.09s | 6ms | 16% |
| redis | 400 | 400 | 100% | 569KB | 84회 | 0.14s | 3ms | 17% |
| redis | 800 | 800 | 100% | 570KB | 178회 | 0.26s | 5ms | 25% |
| redis | 1600 | **1176** | **74%** | 575KB | 125회 | **1.99s** | **800ms** | 95% |
| local | 100 | 100 | 100% | 166KB | 6회 | 0.09s | 5ms | 14% |
| local | 400 | 400 | 100% | 169KB | 27회 | 0.12s | 5ms | 13% |
| local | 800 | 800 | 100% | 168KB | 57회 | 0.13s | 2ms | 15% |
| local | 1600 | **1600** | **100%** | 168KB | 114회 | 0.52s | 68ms | **47%** |

결과를 보면서 제일 놀랐던 건 **800 rps까지는 세 방식이 구분이 안 된다**는 것이었다. 셋 다 목표를 100% 채우고 p99도 한 자릿수 ms다. 여유가 있는 구간에서는 캐시를 넣든 안 넣든 티가 안 난다.

차이는 한계 근처에서만 드러났다. 1,600 rps에서 캐시 없이는 58%, Redis는 74%밖에 못 채웠는데 **로컬 캐시는 100%를 채웠다.** 그것도 CPU 47%로. 아직 한계에 닿지도 않은 것이다.

**처리량 한계가 927 → 1,176 → 1,600 rps로, 로컬 캐시가 1.7배를 만들어냈다.**

이게 선착순 서비스에서 중요한 이유가 있다. 평소 트래픽에서는 어느 방식이든 잘 돌아간다. 문제는 티켓 오픈 순간처럼 트래픽이 순간적으로 튀는 때인데, 그때 버티는 폭이 바로 이 차이다.

### GC값이 다르다?
모니터링을 진행하던중에 다른 값이 있는데 gc값이 다른이유를 살펴보았다.
gc가 발생하는 원인들을 찾아보았다.

먼저 알아야 할 것은 **GC 빈도를 결정하는 것은 할당 속도**라는 점이다. Eden 영역이 차는 속도가 곧 GC 주기다. 그래서 "요청당 몇 바이트를 새로 만드는가"를 보면 GC 차이가 설명된다.

세 방식의 요청당 할당은 **765KB / 570KB / 168KB**였다. 각 경로가 무엇을 할당하는지 뜯어보면 이렇다.

**mysql을 직접 조회할때**

1. JDBC 드라이버가 네트워크에서 바이트를 읽는다. **DB는 압축 없이 보내기 때문에 150KB가 그대로 온다**
2. ResultSet에서 컬럼을 String으로 만든다 (`@Lob` 안내문이 통째로 String이 된다)
3. Hibernate가 엔티티를 만들고 필드를 채운다
4. 영속성 컨텍스트에 등록하고 부기(bookkeeping) 객체들을 만든다

여기서 3번과 4번은 페이로드 크기와 무관한 **고정 비용**이고, 1번과 2번은 크기에 비례한다.

**redis를 조회할때**

1. Lettuce가 네트워크에서 바이트를 읽는다. **압축된 상태라 몇 KB뿐이다** — 여기서 크게 아낀다
2. gunzip으로 압축을 푼다 → 150KB짜리 배열이 새로 생긴다
3. `readAllBytes()`로 그걸 또 배열에 담는다 → **150KB 사본이 하나 더 생긴다**
4. Jackson이 파싱하면서 String을 만든다 (한글이라 UTF-16으로 약 100KB)

합치면 조회당 약 418KB다. 페이로드 150KB의 2.8배쯤 되는데, 실측으로 Redis와 로컬의 차이가 정확히 이 정도(요청당 138KB × 조회 비율)로 나왔다.

**로컬 캐시에서 꺼낼때**

이미 만들어 둔 객체의 참조를 그대로 돌려준다. **네트워크도, 압축 해제도, 파싱도 없다.** 페이로드가 크든 작든 조회당 할당이 사실상 0이다.

정리하면 이렇게 된다.

- DB는 **비압축 전송 + Hibernate 오버헤드**를 낸다
- Redis는 **전송은 아끼지만 압축 해제와 파싱**을 낸다
- 로컬은 **둘 다 없다**

그래서 GC 횟수가 갈렸다. 1,600 rps 구간에서 총 정지 시간이 1.87s / 1.99s / **0.52s**로 나온 게 이 차이의 결과다. GC 오버헤드 자체는 1.3% / 0.9% / 0.4%로 다 낮아 보이지만, 정지가 몰리는 순간 p99가 998ms / 800ms / 68ms로 갈라진다.

### 한 가지 반전

처음에 나는 "Redis 캐싱을 하면 역직렬화 때문에 GC가 더 발생할 것"이라고 예상했다. 참고했던 글에서도 그런 현상을 관찰했다고 했고.

**그런데 재보니 반대였다.** Redis는 어떤 크기에서도 DB보다 적게 할당했다. 압축이 네트워크 바이트를 줄여주는 이득이, gzip 해제 비용보다 컸기 때문이다.

돌이켜보면 이건 **압축을 적용했기 때문에 얻은 결과**다. 압축 없이 JSON을 그대로 넣었다면 Redis에서도 150KB를 그대로 받아 파싱했을 테니, DB와 비슷하거나 더 나빴을 가능성이 크다. "Redis 캐싱이 GC를 유발한다"는 서술은 압축을 안 했거나 값이 훨씬 클 때 성립하는 이야기로 보인다.

### 데이터 크기가 작으면 아무것도 안 보인다

마지막으로 꼭 남겨두고 싶은 게 있다. 처음에 안내문 9KB로 쟀을 때는 **로컬 캐시가 GC를 전혀 줄이지 못했다.** 오히려 할당이 7% 늘어난 것으로 나왔다.

캐시가 고장난 줄 알고 지표를 뜯어봤더니 이랬다.

```
로컬 히트    800회      ← 캐시는 제대로 작동했다
로컬 미스      5회      ← TTL 30초 × 161초 = 5.4회, 정확히 일치
Redis 호출     5회      ← 압축 해제가 805번에서 5번으로
```

역직렬화 800번을 없앴는데도 할당이 안 줄었다. 계산해보니 **그 800번이 전체 할당의 2.4%였다.**

```
한 번당 약 25KB × 800회 = 약 20MB
구간 총 할당 836MB 중 2.4%
```

기대 절감(-2.4%)이 실행 간 잡음(±3.5%)보다 작으니 측정될 리가 없었던 것이다. 캐시가 잘못된 게 아니라 **재려는 대상이 애초에 너무 작았다.**

이 경험에서 배운 건, 최적화의 효과를 재려면 **그 최적화가 건드리는 부분이 전체에서 차지하는 비중**을 먼저 확인해야 한다는 것이다. 2%짜리를 없애고 나서 아무 변화가 없다고 실망할 게 아니라, 애초에 2%짜리인 걸 알고 시작했어야 했다.


# 앞으로 고도화할 것들

## 1. `readAllBytes()` 제거

역직렬화 코드를 다시 보면 개선 여지가 있다.

```java
return objectMapper.readValue(in.readAllBytes(), type);
```

`readAllBytes()`가 압축 푼 전체를 배열로 한 번 더 만들고, 그 과정에서 중간 청크 복사도 한다. 스트리밍으로 바꾸면 그 사본이 사라진다.

```java
return objectMapper.readValue(in, type);
```

데이터가 클수록 이득이 커지는 방향이라 우선순위가 높다.

## 2. 요청당 168KB의 정체 밝히기

로컬 캐시가 히트해도 사라지지 않는 바닥값이 168KB다. 응답 본문이 116KB인데 그 1.5배를 할당하고 있다. 응답 직렬화와 `ContentCachingResponseWrapper`(로그 필터가 응답을 버퍼에 담는다)가 후보지만 아직 측정하지 않았다. JFR로 할당 프로파일을 뜨면 나올 것이다.

**GC를 실제로 움직이는 건 이제 캐시가 아니라 이 168KB다.** 캐시를 더 손봐야 만지는 건 전체의 일부인데, 이쪽은 모든 요청에 붙는 비용이다.

## 3. 캐시 스탬피드 대비

지금 구조에는 구멍이 하나 있다. 인기 행사의 캐시가 만료되는 순간, 동시에 들어온 요청이 전부 캐시 미스로 DB에 몰린다. 선착순 오픈 직전이라면 그 수가 상당할 것이다.

TTL에 지터를 주거나, 캐시 미스 시 한 요청만 DB를 치고 나머지는 기다리게 하는 방식(single-flight)을 검토해볼 만하다.

## 4. 목록 조회 캐싱

지금은 상세 조회만 캐싱한다. 목록 조회는 `scope=owned` / `participated`라 사용자별이어서 캐시 키가 사용자마다 생기고, 캠페인 하나만 바뀌어도 그게 포함된 모든 사용자의 목록이 무효가 된다. 공개 목록 API가 생긴다면 그때는 캐싱 대상으로 훨씬 매력적일 것이다.

## 5. 압축 라이브러리 재검토

지금은 gzip을 쓰고 있는데, 압축·해제 비용이 실제로 문제가 되는 구간에 도달하면 Zstd나 LZ4를 다시 볼 생각이다. 다만 그 전에 위의 1, 2번을 먼저 해야 한다. 지금 병목은 압축 알고리즘이 아니다.
