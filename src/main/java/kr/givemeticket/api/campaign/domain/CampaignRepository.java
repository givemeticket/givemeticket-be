package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository {

    Campaign save(Campaign campaign);

    Optional<Campaign> findById(Long campaignId);

    Optional<Campaign> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * 내가 만든 행사 목록. 삭제된 것도 함께 내려간다 — 목록에서 조용히 사라지는 대신
     * status=DELETED 로 "삭제됨"이라고 보여줄 수 있어야 한다.
     */
    List<Campaign> findAllOwnedBy(Long ownerId);

    /**
     * 아직 살아 있는 내 행사만. 삭제 처리 대상을 고를 때 쓴다.
     */
    List<Campaign> findAllLiveOwnedBy(Long ownerId);

    List<Campaign> findAllByIdIn(Collection<Long> campaignIds);

    List<Campaign> findAllByStatusAndOpenAtLessThanEqual(CampaignStatus status, LocalDateTime now);

    /**
     * @return 실제로 바뀐 행 수. 0이면 그 사이 다른 요청이 이미 삭제한 것이다
     */
    int markDeleted(Long campaignId);
}
