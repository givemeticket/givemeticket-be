package kr.givemeticket.api.global.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * JSON 으로 직렬화한 뒤 gzip 으로 압축해서 Redis 에 넣는다.
 *
 * <p>노리는 것은 Redis 메모리와 <b>네트워크로 오가는 바이트 수</b>다. Redis 는 단일 스레드라
 * 큰 값을 주고받는 동안 다른 요청이 그만큼 밀린다. 값이 클수록 압축이 이득이다.
 *
 * <p>공짜는 아니다. 압축·해제가 요청마다 CPU 를 쓰고 중간 버퍼를 힙에 만든다.
 * 값이 작으면 gzip 헤더(약 20바이트)와 CPU 값만 치르고 얻는 게 없을 수 있어서,
 * 원본과 압축 후 크기를 둘 다 지표로 남긴다. 실제로 줄었는지 보고 판단하라.
 *
 * <p>지표: {@code campaign_cache_value_size_bytes{state="raw|compressed"}}
 */
public class GzipRedisSerializer<T> implements RedisSerializer<T> {

    private static final byte[] EMPTY = new byte[0];

    private final Class<T> type;
    private final ObjectMapper objectMapper;
    private final DistributionSummary rawSize;
    private final DistributionSummary compressedSize;

    public GzipRedisSerializer(
            Class<T> type,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            String cacheName
    ) {
        this.type = type;
        this.objectMapper = objectMapper;
        this.rawSize = sizeSummary(meterRegistry, cacheName, "raw");
        this.compressedSize = sizeSummary(meterRegistry, cacheName, "compressed");
    }

    /**
     * 캐시 값 전용 ObjectMapper.
     *
     * <p>모르는 필드는 무시한다. 스냅샷에 필드를 추가한 배포 직후, Redis 에 남아 있는 옛 값을
     * 읽다가 전부 터지는 것보다 조용히 무시하고 TTL 로 갈리는 편이 낫다.
     */
    public static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static DistributionSummary sizeSummary(MeterRegistry registry, String cacheName, String state) {
        return DistributionSummary.builder(cacheName + ".cache.value.size")
                .description("캐시에 담는 값의 크기. raw 와 compressed 를 나눠 봐야 압축률이 나온다")
                .baseUnit("bytes")
                .tag("state", state)
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return EMPTY;
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            byte[] compressed = gzip(json);

            rawSize.record(json.length);
            compressedSize.record(compressed.length);

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
}
