package kr.givemeticket.api.campaign.infrastructure;

import java.util.Optional;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * {@code campaign.cache.enabled=false} 일 때 들어가는 빈.
 *
 * <p>캐시 코드를 들어내지 않고 같은 빌드에서 캐시 있음/없음을 비교하려고 둔다.
 * Redis 를 아예 부르지 않으므로 기준선 측정에 캐시가 섞이지 않는다.
 */
@Repository
@ConditionalOnProperty(name = "campaign.cache.enabled", havingValue = "false")
public class NoOpCampaignCacheRepository implements CampaignCacheRepository {

    @Override
    public Optional<CampaignSnapshot> find(String shortCode) {
        return Optional.empty();
    }

    @Override
    public void save(CampaignSnapshot snapshot) {
        // 아무것도 하지 않는다
    }

    @Override
    public void evict(String shortCode) {
        // 아무것도 하지 않는다
    }
}
