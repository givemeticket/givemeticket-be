package kr.givemeticket.api.campaign.application;

import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
