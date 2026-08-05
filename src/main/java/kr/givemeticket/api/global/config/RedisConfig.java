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
}
