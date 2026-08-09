package kr.givemeticket.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
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
