package kr.givemeticket.api.campaign.infrastructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisStockRepository implements StockRepository {

    private static final String STOCK_KEY_PREFIX = "campaign:stock:";
    private static final String APPLICANTS_KEY_PREFIX = "campaign:applicants:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> seatReserveScript;
    private final RedisScript<Long> seatRestoreScript;

    @Override
    public void initialize(Long campaignId, int totalStock) {
        stringRedisTemplate.opsForValue().set(key(campaignId), String.valueOf(totalStock));
    }

    @Override
    public StockDecreaseResult decrease(Long campaignId, Long userId) {
        Long result = stringRedisTemplate.execute(
                seatReserveScript,
                List.of(key(campaignId), applicantsKey(campaignId)),
                String.valueOf(userId));

        long value = (result == null) ? -1L : result;
        if (value == -1L) {
            return StockDecreaseResult.notInitialized();
        }
        if (value == -2L) {
            return StockDecreaseResult.soldOut();
        }
        if (value == -3L) {
            return StockDecreaseResult.alreadyApplied();
        }
        return StockDecreaseResult.success(value);
    }

    @Override
    public void restore(Long campaignId, Long userId, int upperBound) {
        stringRedisTemplate.execute(
                seatRestoreScript,
                List.of(key(campaignId), applicantsKey(campaignId)),
                String.valueOf(upperBound), String.valueOf(userId));
    }

    @Override
    public void increaseBy(Long campaignId, int delta) {
        stringRedisTemplate.opsForValue().increment(key(campaignId), delta);
    }

    @Override
    public Long getRemaining(Long campaignId) {
        String value = stringRedisTemplate.opsForValue().get(key(campaignId));
        return (value == null) ? null : Long.parseLong(value);
    }

    /**
     * MGET 한 번으로 끝낸다. 값이 없는 자리는 null 로 오는데, 그 캠페인은 결과에서 뺀다.
     */
    @Override
    public Map<Long, Long> getRemainingAll(Collection<Long> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = List.copyOf(campaignIds);
        List<String> values = stringRedisTemplate.opsForValue()
                .multiGet(ids.stream().map(this::key).toList());

        if (values == null) {
            return Map.of();
        }

        Map<Long, Long> remainingById = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            String value = values.get(i);
            if (value != null) {
                remainingById.put(ids.get(i), Long.parseLong(value));
            }
        }
        return remainingById;
    }

    @Override
    public void remove(Long campaignId) {
        stringRedisTemplate.delete(List.of(key(campaignId), applicantsKey(campaignId)));
    }

    private String key(Long campaignId) {
        return STOCK_KEY_PREFIX + campaignId;
    }

    /** 자리를 잡은 사용자들. 중복 판정의 근거이며, 원소 수는 정원으로 상한이 잡힌다. */
    private String applicantsKey(Long campaignId) {
        return APPLICANTS_KEY_PREFIX + campaignId;
    }
}
