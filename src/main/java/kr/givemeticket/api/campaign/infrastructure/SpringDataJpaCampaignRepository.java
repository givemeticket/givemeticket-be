package kr.givemeticket.api.campaign.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaCampaignRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    List<Campaign> findAllByOwnerIdAndStatusNotOrderByIdDesc(Long ownerId, CampaignStatus excluded);

    List<Campaign> findAllByIdInOrderByIdDesc(Collection<Long> ids);

    List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now);
}
