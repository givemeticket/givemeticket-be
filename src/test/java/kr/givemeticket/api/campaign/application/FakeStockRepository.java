package kr.givemeticket.api.campaign.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;

class FakeStockRepository implements StockRepository {

    final Map<Long, Long> stock = new LinkedHashMap<>();

    /**
     * Redis 가 죽은 상황을 흉내낸다.
     */
    boolean failing;

    /**
     * 일괄 조회가 정말 한 번만 도는지 보기 위한 호출 수.
     */
    int batchReadCount;

    @Override
    public void increaseBy(Long campaignId, int delta) {
        stock.merge(campaignId, (long) delta, Long::sum);
    }

    @Override
    public void initialize(Long campaignId, int totalStock) {
        stock.put(campaignId, (long) totalStock);
    }

    @Override
    public Long getRemaining(Long campaignId) {
        if (failing) {
            throw new IllegalStateException("redis down");
        }
        return stock.get(campaignId);
    }

    @Override
    public Map<Long, Long> getRemainingAll(Collection<Long> campaignIds) {
        batchReadCount++;
        if (failing) {
            throw new IllegalStateException("redis down");
        }

        Map<Long, Long> remaining = new HashMap<>();
        for (Long campaignId : campaignIds) {
            Long value = stock.get(campaignId);
            if (value != null) {
                remaining.put(campaignId, value);
            }
        }
        return remaining;
    }

    @Override
    public StockDecreaseResult decrease(Long campaignId, Long userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restore(Long campaignId, Long userId, int upperBound) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(Long campaignId) {
        stock.remove(campaignId);
    }
}
