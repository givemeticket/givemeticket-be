package kr.givemeticket.api.campaign.application;

import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 캠페인이 바뀌었을 때 캐시를 걷어낸다.
 *
 * <p>두 번 지운다. 한 번은 지금, 한 번은 트랜잭션이 끝난 뒤다.
 * 지금만 지우면 커밋 전에 들어온 조회가 <b>바뀌기 전 값</b>을 읽어 캐시를 다시 채워 버리고,
 * 그 값이 TTL 이 만료될 때까지 남는다. 커밋 뒤 한 번 더 지워야 그 창을 닫을 수 있다.
 *
 * <p>롤백된 경우에도 지운다. 지울 필요 없는 캐시를 지운 대가는 캐시 미스 한 번뿐이라,
 * 낡은 값을 남기는 쪽보다 항상 싸다.
 */
@Component
@RequiredArgsConstructor
public class CampaignCacheEvictor {

    private final CampaignCacheRepository campaignCacheRepository;

    public void evict(String shortCode) {
        campaignCacheRepository.evict(shortCode);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                campaignCacheRepository.evict(shortCode);
            }
        });
    }
}
