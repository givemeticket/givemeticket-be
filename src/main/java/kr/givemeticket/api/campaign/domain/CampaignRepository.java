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
     * 제목에 검색어가 들어간, 삭제되지 않은 행사를 최신순으로. id 가 {@code cursor} 보다 작은 것만 본다.
     *
     * @param keyword 사용자가 입력한 그대로. %·_ 같은 와일드카드 문자도 글자로 찾는다
     * @param cursor  첫 페이지면 null
     */
    List<Campaign> searchLiveByTitle(String keyword, Long cursor, int limit);

    /**
     * 한 사람이 연, 삭제되지 않은 행사를 최신순으로. 남이 보는 목록이라 삭제된 행사는 뺀다.
     *
     * @param cursor 첫 페이지면 null
     */
    List<Campaign> findLivePageOwnedBy(Long ownerId, Long cursor, int limit);

    /**
     * 행사 시작 일시가 (from, to] 에 드는, 삭제되지 않은 행사. 임박 알림 대상을 고른다.
     */
    List<Campaign> findAllLiveByEventAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * @return 실제로 바뀐 행 수. 0이면 그 사이 다른 요청이 이미 삭제한 것이다
     */
    int markDeleted(Long campaignId);
}
