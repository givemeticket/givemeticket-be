package kr.givemeticket.api.campaign.infrastructure;

import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisCampaignStateRepository implements CampaignStateRepository {

    private static final String OPEN_KEY_PREFIX = "campaign:open:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void open(Long campaignId) {
        stringRedisTemplate.opsForValue().set(key(campaignId), "1");
    }

    @Override
    public boolean isOpen(Long campaignId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(campaignId)));
    }

    private String key(Long campaignId) {
        return OPEN_KEY_PREFIX + campaignId;
    }
}
