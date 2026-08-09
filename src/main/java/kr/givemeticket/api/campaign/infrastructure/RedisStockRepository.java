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

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> stockDecreaseScript;
    private final RedisScript<Long> stockRestoreScript;

    @Override
    public void initialize(Long campaignId, int totalStock) {
        stringRedisTemplate.opsForValue().set(key(campaignId), String.valueOf(totalStock));
    }

    @Override
    public StockDecreaseResult decrease(Long campaignId) {
        Long result = stringRedisTemplate.execute(
                stockDecreaseScript, List.of(key(campaignId)));

        long value = (result == null) ? -1L : result;
        if (value == -1L) {
            return StockDecreaseResult.notInitialized();
        }
        if (value == -2L) {
            return StockDecreaseResult.soldOut();
        }
        return StockDecreaseResult.success(value);
    }

    @Override
    public void restore(Long campaignId, int upperBound) {
        stringRedisTemplate.execute(
                stockRestoreScript, List.of(key(campaignId)), String.valueOf(upperBound));
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

    @Override
    public Map<Long, Long> getRemaining(Collection<Long> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(campaignIds);
        List<String> values = stringRedisTemplate.opsForValue()
                .multiGet(ids.stream().map(this::key).toList());

        Map<Long, Long> remaining = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            String value = (values == null) ? null : values.get(i);
            remaining.put(ids.get(i), (value == null) ? null : Long.parseLong(value));
        }
        return remaining;
    }

    @Override
    public void remove(Long campaignId) {
        stringRedisTemplate.delete(key(campaignId));
    }

    private String key(Long campaignId) {
        return STOCK_KEY_PREFIX + campaignId;
    }
}
