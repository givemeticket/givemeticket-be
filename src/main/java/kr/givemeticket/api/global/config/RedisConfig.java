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

    /**
     * 메인 큐 적재. XADD 와 트리밍을 한 왕복에 끝낸다.
     *
     * <p>XACK 은 스트림을 비우지 않고 PEL 에서만 뺀다. 트리밍이 없으면 <b>성공한
     * 메시지까지</b> 쌓이므로 MAXLEN 을 함께 건다. {@code ~} 는 근사 트리밍이다.
     *
     * <p>KEYS[1] = 스트림, ARGV[1] = MAXLEN, ARGV[2..] = 필드-값 쌍
     */
    @Bean
    public RedisScript<String> reservationPublishScript() {
        String script = """
                return redis.call('XADD', KEYS[1], 'MAXLEN', '~', ARGV[1], '*', unpack(ARGV, 2))
                """;
        return new DefaultRedisScript<>(script, String.class);
    }

    /**
     * 채번 카운터를 최소값 이상으로 올린다. 기동 시 한 번 부른다.
     *
     * <p>이미 더 높으면 그대로 둔다 — 발급됐지만 아직 저장되지 않은 번호를 되감으면
     * 같은 PK 로 두 예매가 부딪힌다.
     *
     * <p>KEYS[1] = 카운터, ARGV[1] = 최소값
     */
    @Bean
    public RedisScript<Long> applicationIdSeedScript() {
        String script = """
                local current = tonumber(redis.call('GET', KEYS[1]))
                local minimum = tonumber(ARGV[1])
                if current == nil or current < minimum then
                    redis.call('SET', KEYS[1], minimum)
                    return minimum
                end
                return current
                """;
        return new DefaultRedisScript<>(script, Long.class);
    }

    /**
     * 자리 하나를 잡는다. 중복 확인과 재고 차감이 한 덩어리다.
     *
     * <p>영속화가 비동기가 되면서 중복 판정이 DB 를 떠났다. 두 검사를 나눠 쏘면 같은
     * 사람이 동시에 두 번 눌렀을 때 둘 다 통과하므로 한 스크립트에 둔다.
     *
     * <p>중복을 재고보다 <b>먼저</b> 본다. 이미 자리를 가진 사람에게 "매진"이라고
     * 답할 수는 없다.
     *
     * <p>KEYS[1] = 재고, KEYS[2] = 신청자 집합, ARGV[1] = userId
     *
     * @return 남은 재고. -1 초기화 안 됨, -2 매진, -3 이미 신청함
     */
    /**
     * 실행 시각이 된 메시지를 지연 큐에서 메인 큐로 되돌린다.
     *
     * <p>꺼내기와 넣기가 <b>한 덩어리</b>여야 한다. 나누면 그 사이에 죽었을 때 메시지가
     * 어느 쪽에도 없다. {@code ZREM} 이 1 을 돌려준 것만 넣어, 인스턴스 여럿이 같은
     * 멤버를 집어도 중복이 생기지 않게 한다.
     *
     * <p>KEYS[1] = 지연 큐, KEYS[2] = 메인 큐
     * <br>ARGV[1] = 현재 시각(ms), ARGV[2] = 최대 개수, ARGV[3] = MAXLEN
     */
    @Bean
    public RedisScript<Long> reservationRetryPromoteScript() {
        String script = """
                local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1],
                                       'LIMIT', 0, ARGV[2])
                local moved = 0
                for _, member in ipairs(due) do
                    if redis.call('ZREM', KEYS[1], member) == 1 then
                        local fields = {}
                        for token in string.gmatch(member, '([^|]+)') do
                            fields[#fields + 1] = token
                        end
                        redis.call('XADD', KEYS[2], 'MAXLEN', '~', ARGV[3], '*', unpack(fields))
                        moved = moved + 1
                    end
                end
                return moved
                """;
        return new DefaultRedisScript<>(script, Long.class);
    }

    @Bean
    public RedisScript<Long> seatReserveScript() {
        String script = """
                if redis.call('EXISTS', KEYS[1]) == 0 then
                    return -1
                end
                if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
                    return -3
                end
                local stock = tonumber(redis.call('GET', KEYS[1]))
                if stock <= 0 then
                    return -2
                end
                redis.call('SADD', KEYS[2], ARGV[1])
                return redis.call('DECR', KEYS[1])
                """;
        return new DefaultRedisScript<>(script, Long.class);
    }

    /**
     * 실패·취소로 자리를 되돌린다. 상한(정원)을 넘으면 재고는 건드리지 않아,
     * 중복 호출돼도 재고가 정원보다 커지지 않는다.
     *
     * <p>신청자 집합에서 빼는 일은 <b>상한과 무관하게 언제나</b> 한다. 남겨두면
     * 자리도 없는데 재신청이 막힌다.
     *
     * <p>KEYS[1] = 재고, KEYS[2] = 신청자 집합, ARGV[1] = 정원, ARGV[2] = userId
     */
    @Bean
    public RedisScript<Long> seatRestoreScript() {
        String script = """
                redis.call('SREM', KEYS[2], ARGV[2])
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
