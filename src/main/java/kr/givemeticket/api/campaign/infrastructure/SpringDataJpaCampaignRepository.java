package kr.givemeticket.api.campaign.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaCampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now);
}
