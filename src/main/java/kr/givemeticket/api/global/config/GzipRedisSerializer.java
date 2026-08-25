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
