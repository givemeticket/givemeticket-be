package kr.givemeticket.api.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 캠페인 캐시 전용 템플릿. 값은 gzip 으로 눌러서 넣는다.
     *
     * <p>ObjectMapper 를 애플리케이션 공용 빈에서 가져오지 않고 여기서 따로 만든다.
     * 응답 JSON 설정을 바꿨다고 캐시에 이미 쌓인 값의 포맷이 조용히 달라지면 안 되기 때문이다.
     */
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

    @Bean
    public RedisScript<Long> stockDecreaseScript() {
        String script = """
                if redis.call('EXISTS', KEYS[1]) == 0 then
                    return -1
                end
                local stock = tonumber(redis.call('GET', KEYS[1]))
                if stock <= 0 then
                    return -2
                end
                return redis.call('DECR', KEYS[1])
                """;
        return new DefaultRedisScript<>(script, Long.class);
    }

    /**
     * 실패·취소로 자리를 되돌린다. 상한(정원)을 넘으면 아무것도 하지 않는다.
     * 복원이 중복 호출돼도 재고가 정원보다 커지지 않게 막는 안전장치다.
     */
    @Bean
    public RedisScript<Long> stockRestoreScript() {
        String script = """
                local current = tonumber(redis.call('GET', KEYS[1]))
                if current == nil then
                    return -1
                end
                if current >= tonumber(ARGV[1]) then
                    return current
                end
                return redis.call('INCR', KEYS[1])
                """;
        return new DefaultRedisScript<>(script, Long.class);
    }
}
