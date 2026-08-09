package kr.givemeticket.api.campaign.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignScheduler {

    private final CampaignRepository campaignRepository;
    private final CampaignStateRepository campaignStateRepository;

    @Scheduled(fixedDelayString = "${campaign.open-scheduler-delay-ms:1000}")
    @Transactional
    public void openScheduledCampaigns() {
        List<Campaign> targets = campaignRepository.findAllByStatusAndOpenAtLessThanEqual(
                CampaignStatus.SCHEDULED, LocalDateTime.now());

        for (Campaign campaign : targets) {
            campaign.open();
            campaignStateRepository.save(campaign.getId(),
                    new CampaignState(campaign.isRequiresPayment(), campaign.getTotalStock()));
            log.info("campaign opened: id={}, title={}", campaign.getId(), campaign.getTitle());
        }
    }
}
