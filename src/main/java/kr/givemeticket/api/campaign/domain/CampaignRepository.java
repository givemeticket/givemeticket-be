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
     * 조건에 맞는 행사를 정렬 순서대로, 커서 다음부터 최대 {@code limit} 건.
     *
     * <p>매진 여부는 Redis 재고가 아니라 확정된 신청 수로 판단한다. 조건과 정렬, 전체 건수를 한
     * 쿼리 안에서 맞추려면 DB 에 있는 값이어야 한다. 방금 잡힌 자리는 신청 행이 저장되기 전까지
     * (보통 1초 안) 세지 않는다.
     *
     * @param cursor 첫 페이지면 null
     */
    List<Campaign> search(CampaignSearchCondition condition, CampaignCursor cursor, int limit);

    /** 조건에 맞는 전체 건수. 커서와 상관없다. */
    long count(CampaignSearchCondition condition);

    /** 오픈 전이고 오픈 시각이 (from, to] 에 드는 행사. 찜한 사람에게 오픈 임박을 알린다. */
    List<Campaign> findAllScheduledByOpenAtBetween(LocalDateTime from, LocalDateTime to);

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
