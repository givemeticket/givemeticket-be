package kr.givemeticket.api.apply.infrastructure;

import java.util.List;
import kr.givemeticket.api.apply.domain.ApplicationIdIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * 예매 식별자 채번의 Redis 구현. 단순 {@code INCR} 이다.
 *
 * <p>언제: 신청마다 한 번. 기동 시에는 {@link #seedAtLeast} 로 카운터를 맞춘다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisApplicationIdIssuer implements ApplicationIdIssuer {

    static final String ID_KEY = "application:id";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> applicationIdSeedScript;

    @Override
    public long issue() {
        Long id = stringRedisTemplate.opsForValue().increment(ID_KEY);
        if (id == null) {
            // INCR 이 값을 안 돌려줬다는 건 연결이 끊겼다는 뜻이다.
            throw new IllegalStateException("예매 식별자를 채번하지 못했다");
        }
        return id;
    }

    /**
     * 카운터를 {@code minimum} 이상으로 끌어올린다. <b>이미 더 높으면 건드리지 않는다</b> —
     * 발급했지만 아직 저장되지 않은 번호를 되감으면 안 되기 때문이다.
     *
     * @return 호출 이후 카운터 값
     */
    public long seedAtLeast(long minimum) {
        Long value = stringRedisTemplate.execute(
                applicationIdSeedScript, List.of(ID_KEY), String.valueOf(minimum));
        return (value == null) ? minimum : value;
    }
}
