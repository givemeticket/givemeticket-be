package kr.givemeticket.api.campaign.infrastructure;

import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisCampaignStateRepository implements CampaignStateRepository {

    private static final String STATE_KEY_PREFIX = "campaign:state:";
    private static final String FIELD_REQUIRES_PAYMENT = "requiresPayment";
    private static final String FIELD_TOTAL_STOCK = "totalStock";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void save(Long campaignId, CampaignState state) {
        stringRedisTemplate.opsForHash().putAll(key(campaignId), Map.of(
                FIELD_REQUIRES_PAYMENT, String.valueOf(state.requiresPayment()),
                FIELD_TOTAL_STOCK, String.valueOf(state.totalStock())
        ));
    }

    @Override
    public Optional<CampaignState> find(Long campaignId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key(campaignId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CampaignState(
                Boolean.parseBoolean((String) entries.get(FIELD_REQUIRES_PAYMENT)),
                Integer.parseInt((String) entries.get(FIELD_TOTAL_STOCK))
        ));
    }

    @Override
    public void remove(Long campaignId) {
        stringRedisTemplate.delete(key(campaignId));
    }

    private String key(Long campaignId) {
        return STATE_KEY_PREFIX + campaignId;
    }
}
