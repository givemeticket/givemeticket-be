package kr.givemeticket.api.campaign.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignOpenedEvent;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignScheduler {

    private final CampaignRepository campaignRepository;
    private final CampaignStateRepository campaignStateRepository;
    private final CampaignCacheEvictor campaignCacheEvictor;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${campaign.open-scheduler-delay-ms:1000}")
    @Transactional
    public void openScheduledCampaigns() {
        List<Campaign> targets = campaignRepository.findAllByStatusAndOpenAtLessThanEqual(
                CampaignStatus.SCHEDULED, LocalDateTime.now());

        for (Campaign campaign : targets) {
            campaign.open();
            campaignStateRepository.save(campaign.getId(),
                    new CampaignState(campaign.getTotalStock()));
            campaignCacheEvictor.evict(campaign.getShortCode());
            // 찜한 사람에게 오픈 알림을 보낸다. 기록은 오픈이 커밋된 뒤에 한다 — 알림이 실패해도 오픈은 된다.
            eventPublisher.publishEvent(new CampaignOpenedEvent(
                    campaign.getId(), campaign.getTitle(), campaign.getShortCode(), campaign.getOpenAt()));
            log.info("campaign opened: id={}, title={}", campaign.getId(), campaign.getTitle());
        }
    }
}
