package kr.givemeticket.api.campaign.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kr.givemeticket.api.apply.application.ApplicationService;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.application.dto.CampaignDetailCommand;
import kr.givemeticket.api.campaign.application.dto.request.CampaignCreateRequest;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignOwnerInfo;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignStockResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSummaryResponse;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.ShortCodeGenerator;
import kr.givemeticket.api.campaign.domain.StockRepository;
import kr.givemeticket.api.campaign.domain.ViewerRole;
import kr.givemeticket.api.user.application.UserService;
import kr.givemeticket.api.user.application.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private static final int SHORT_CODE_MAX_ATTEMPTS = 5;
    private static final Set<ApplicationStatus> CONFIRMED_ONLY = Set.of(ApplicationStatus.CONFIRMED);

    /**
     * 취소됐어도 "나의 티켓"에 남겨야 하는 사유. 사용자가 직접 누르지 않은 취소만 여기 들어간다.
     */
    private static final Set<FailureReason> LISTED_CANCELLATIONS = Set.of(FailureReason.CAMPAIGN_DELETED);

    private final CampaignRepository campaignRepository;
    private final CampaignPersister campaignPersister;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final StockRepository stockRepository;
    private final CampaignStateRepository campaignStateRepository;
    private final CampaignCacheRepository campaignCacheRepository;
    private final CampaignCacheEvictor campaignCacheEvictor;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UserService userService;

    @Transactional
    public CampaignResponse createCampaign(Long ownerId, CampaignCreateRequest request) {
        Campaign campaign = new Campaign(
                ownerId,
                generateShortCode(),
                request.title(),
                CampaignType.TICKET,
                request.totalStock(),
                request.openAt(),
                CampaignDetailCommand.toCampaignDetailOrNull(request.detail()));
        Campaign saved = campaignRepository.save(campaign);

        stockRepository.initialize(saved.getId(), saved.getTotalStock());

        return CampaignResponse.of(saved);
    }

    /**
     * 재고만 따로 내려준다. 상세·목록보다 훨씬 자주 폴링되므로 DB를 거치지 않고 Redis만 읽는다.
     *
     * <p>재고 키는 생성 시 만들어져 삭제 시에만 지워진다. 키가 없으면 없거나 삭제된 캠페인이라
     * 404로 끊는다. 삭제 여부를 410으로 구분하려면 DB를 봐야 하는데, 그러면 분리한 의미가 없다.
     */
    public CampaignStockResponse getStock(Long campaignId) {
        Long remaining = stockRepository.getRemaining(campaignId);
        if (remaining == null) {
            throw CampaignApplicationException.campaignNotFound();
        }
        return CampaignStockResponse.of(campaignId, remaining);
    }

    /**
     * 첫 화면을 한 번에 그릴 수 있도록 개설자 정보와 잔여 재고까지 함께 담아 보낸다.
     * 그 뒤의 재고 갱신은 {@link #getStock(Long)} 이 받는다.
     */
    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaignDetail(String shortCode, Long userId) {
        CampaignSnapshot campaign = findCachedCampaign(shortCode);
        if (campaign.status() == CampaignStatus.DELETED) {
            throw CampaignApplicationException.campaignDeleted();
        }

        CampaignOwnerInfo owner = findOwner(campaign.ownerId());
        Long remainingStock = findRemainingStock(campaign.id());

        if (userId == null) {
            return CampaignDetailResponse.of(
                    campaign, owner, remainingStock, ViewerRole.GUEST, null, null);
        }

        Application mine = applicationRepository
                .findByCampaignIdAndUserId(campaign.id(), userId)
                .orElse(null);

        if (campaign.isOwnedBy(userId)) {
            long confirmedCount = applicationRepository
                    .countByCampaignIdAndStatusIn(campaign.id(), CONFIRMED_ONLY);
            return CampaignDetailResponse.of(
                    campaign, owner, remainingStock, ViewerRole.OWNER, mine, confirmedCount);
        }

        ViewerRole role = (mine != null && mine.isActive()) ? ViewerRole.PARTICIPANT : ViewerRole.VIEWER;
        return CampaignDetailResponse.of(campaign, owner, remainingStock, role, mine, null);
    }

    /**
     * 삭제한 행사도 함께 내려간다. 목록에서 조용히 사라지면 개설자는 지운 것인지
     * 사라진 것인지 알 수 없다. status=DELETED 로 "삭제됨"이라고 보여주면 된다.
     */
    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getOwnedCampaigns(Long ownerId) {
        return toSummaries(campaignRepository.findAllOwnedBy(ownerId), Map.of());
    }

    /**
     * 자리를 잡고 있는 신청에 더해, 주최자가 행사를 지워서 취소된 신청까지 보여준다.
     * 신청해 둔 행사가 아무 설명 없이 목록에서 사라지면 사용자는 무슨 일이 있었는지 알 수 없다.
     * 행사는 status=DELETED 로, 신청은 CANCELLED 로 남아 "삭제된 행사"라고 그릴 수 있다.
     *
     * <p>반대로 내가 직접 취소한 건은 넣지 않는다. 사라진 이유를 이미 알고 있고,
     * 취소한 행사가 목록에 계속 남아 있으면 그게 더 이상하다.
     */
    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getParticipatedCampaigns(Long userId) {
        Map<Long, ApplicationStatus> statusByCampaign = applicationRepository
                .findAllByUserIdAndStatusInOrFailureReasonIn(
                        userId, ApplicationStatus.active(), LISTED_CANCELLATIONS).stream()
                .collect(Collectors.toMap(Application::getCampaignId, Application::getStatus));

        return toSummaries(campaignRepository.findAllByIdIn(statusByCampaign.keySet()), statusByCampaign);
    }

    /**
     * 개설자와 재고는 캠페인마다 조회하지 않고 한 번씩 모아 온다. 카드가 30장이어도
     * 유저 조회 1번, Redis 왕복 1번이다.
     */
    private List<CampaignSummaryResponse> toSummaries(
            List<Campaign> campaigns,
            Map<Long, ApplicationStatus> statusByCampaign
    ) {
        Map<Long, UserResponse> ownerById = userService.findUsers(
                campaigns.stream().map(Campaign::getOwnerId).collect(Collectors.toSet()));
        Map<Long, Long> remainingByCampaign = findRemainingStocks(
                campaigns.stream().map(Campaign::getId).toList());

        return campaigns.stream()
                .map(campaign -> CampaignSummaryResponse.of(
                        campaign,
                        CampaignOwnerInfo.of(
                                campaign.getOwnerId(), ownerById.get(campaign.getOwnerId())),
                        remainingByCampaign.get(campaign.getId()),
                        statusByCampaign.get(campaign.getId())))
                .toList();
    }

    private CampaignSnapshot findCachedCampaign(String shortCode) {
        Optional<CampaignSnapshot> cached = campaignCacheRepository.find(shortCode);
        if (cached.isPresent()) {
            return cached.get();
        }

        Campaign campaign = campaignRepository.findByShortCode(shortCode)
                .orElseThrow(CampaignApplicationException::campaignNotFound);

        CampaignSnapshot snapshot = CampaignSnapshot.from(campaign);
        if (snapshot.isCacheable(LocalDateTime.now())) {
            campaignCacheRepository.save(snapshot);
        }
        return snapshot;
    }

    private CampaignOwnerInfo findOwner(Long ownerId) {
        return CampaignOwnerInfo.of(ownerId, userService.findUser(ownerId).orElse(null));
    }

    /**
     * 여기서의 재고는 화면을 처음 그릴 때 쓰는 참고값이라, 못 읽어도 조회 자체를 실패시키지 않는다.
     * 재고를 정확히 알아야 하는 쪽은 {@link #getStock(Long)} 이고 그쪽은 실패를 감추지 않는다.
     */
    private Long findRemainingStock(Long campaignId) {
        try {
            return stockRepository.getRemaining(campaignId);
        } catch (RuntimeException e) {
            log.warn("failed to read stock for campaign detail: campaignId={}", campaignId, e);
            return null;
        }
    }

    private Map<Long, Long> findRemainingStocks(List<Long> campaignIds) {
        try {
            return stockRepository.getRemainingAll(campaignIds);
        } catch (RuntimeException e) {
            log.warn("failed to read stock for campaign list: campaignCount={}", campaignIds.size(), e);
            return Map.of();
        }
    }

    /**
     * 제한은 이미 열린 행사에만 건다. 아직 열리지 않았으면 신청자가 없으니
     * 오픈 시각도 정원도 자유롭게 고칠 수 있다(오픈 시각이 미래인지는 요청 DTO 가 본다).
     *
     * <p>지금 값과 같은 값이 와도 오류로 보지 않는다. 프론트가 폼 전체를 그대로 보내는 게
     * 자연스러운데, 안 바꾼 필드까지 검사하면 정원만 늘리려 해도 막히기 때문이다.
     */
    @Transactional
    public CampaignResponse updateCampaign(Long campaignId, Long userId, CampaignUpdateRequest request) {
        if (request.isEmpty()) {
            throw CampaignApplicationException.nothingToUpdate();
        }
        Campaign campaign = findManageableCampaign(campaignId, userId);
        // 오픈 시각을 미루면 상태가 SCHEDULED 로 돌아가므로, 판정 기준은 손대기 전에 잡아둔다.
        boolean opened = !campaign.isScheduled();

        if (request.openAt() != null && !request.openAt().isEqual(campaign.getOpenAt())) {
            changeOpenAt(campaign, request.openAt(), opened);
        }

        if (request.totalStock() != null && request.totalStock() != campaign.getTotalStock()) {
            changeTotalStock(campaign, request.totalStock(), opened);
        }

        if (request.detail() != null) {
            campaign.changeDetail(request.detail().toCampaignDetail());
        }

        campaignCacheEvictor.evict(campaign.getShortCode());

        return CampaignResponse.of(campaign);
    }

    /**
     * 열린 행사는 뒤로 미루는 것만 된다. 앞당기면 이미 신청을 놓친 사람이 생긴다.
     *
     * <p>미룰 때는 신청 게이트를 걷어 접수를 멈춘다. 이걸 두고 시각만 바꾸면
     * "아직 안 열린 행사인데 신청은 받는" 상태가 된다. 새 시각이 되면 스케줄러가 다시 연다.
     *
     * <p>여기까지 왔다는 건 값이 실제로 달라졌다는 뜻이다. 그래서 "미래여야 한다"를 여기서 본다.
     * 요청 DTO 에서 보면 이미 오픈된(= openAt 이 과거인) 행사의 폼을 그대로 되돌려 보내는 것조차
     * 막힌다.
     *
     * <p>둘 다 걸리는 요청에는 "미룰 수만 있다"를 먼저 알려준다. 이미 오픈된 행사에서는
     * 그쪽이 사용자가 실제로 어겨야 하는 규칙이다.
     */
    private void changeOpenAt(Campaign campaign, LocalDateTime openAt, boolean opened) {
        if (campaign.isClosed()) {
            // 그냥 두면 미래로 미루는 순간 SCHEDULED 로 돌아가 종료한 행사가 다시 열린다.
            throw CampaignApplicationException.campaignClosed();
        }
        if (opened && !openAt.isAfter(campaign.getOpenAt())) {
            throw CampaignApplicationException.openAtNotDelayable();
        }
        if (!openAt.isAfter(LocalDateTime.now())) {
            throw CampaignApplicationException.openAtNotFuture();
        }
        if (!opened) {
            campaign.changeOpenAt(openAt);
            return;
        }

        campaign.delayOpenAt(openAt);
        campaignStateRepository.remove(campaign.getId());

        log.info("campaign open delayed: campaignId={}, openAt={}", campaign.getId(), openAt);
    }

    /**
     * 열린 행사는 늘리는 것만 된다. 이미 나간 자리를 줄일 방법이 없다.
     *
     * <p>열리기 전이면 줄여도 된다. 아무도 신청하지 않았으므로 Redis 재고는 정원 그대로다.
     */
    private void changeTotalStock(Campaign campaign, int totalStock, boolean opened) {
        if (opened && totalStock < campaign.getTotalStock()) {
            throw CampaignApplicationException.totalStockNotIncreasable();
        }

        Long campaignId = campaign.getId();
        int delta = campaign.changeTotalStock(totalStock);
        stockRepository.increaseBy(campaignId, delta);

        campaignStateRepository.find(campaignId).ifPresent(state ->
                campaignStateRepository.save(campaignId, new CampaignState(totalStock)));

        log.info("campaign stock changed: campaignId={}, delta={}, totalStock={}",
                campaignId, delta, totalStock);
    }

    /**
     * 행사를 종료한다. 신규 신청만 막고, 이미 확정된 신청은 그대로 둔다.
     * 그래서 삭제와 달리 취소가 일어나지 않고 트랜잭션 하나로 끝난다.
     *
     * <p>재고 키는 지우지 않는다. 종료된 뒤에도 몇 자리가 나갔는지는 보여야 한다.
     *
     * <p>두 번 눌러도 결과는 같다. 되돌릴 부작용이 없어 굳이 409로 끊지 않는다.
     */
    @Transactional
    public CampaignResponse closeCampaign(Long campaignId, Long userId) {
        Campaign campaign = findManageableCampaign(campaignId, userId);

        // 신규 신청을 먼저 막는다. 상태부터 바꾸면 그 사이에 들어온 신청이 살아남는다.
        campaignStateRepository.remove(campaignId);

        campaignCacheEvictor.evict(campaign.getShortCode());

        if (!campaign.isClosed()) {
            campaign.close();
            log.info("campaign closed: campaignId={}, ownerId={}", campaignId, userId);
        }
        return CampaignResponse.of(campaign);
    }

    /**
     * 신청자가 있어도 삭제할 수 있다. 남은 신청은 전부 취소된다.
     *
     * <p>신청 수만큼 상태 전이가 일어나므로 트랜잭션으로 감싸지 않는다.
     * 상태 전이는 {@link CampaignPersister}·{@link ApplicationPersister} 가 건별로 짧게 끊는다.
     */
    public void deleteCampaign(Long campaignId, Long userId) {
        Campaign campaign = findManageableCampaign(campaignId, userId);

        // 신규 신청을 먼저 막는다. 이걸 뒤로 미루면 취소하는 사이에 들어온 신청이 살아남는다.
        campaignStateRepository.remove(campaignId);
        campaignCacheEvictor.evict(campaign.getShortCode());

        if (campaignPersister.markDeleted(campaignId) == 0) {
            // 그 사이 다른 요청이 이미 지웠다. 취소를 두 번 돌리지 않는다.
            throw CampaignApplicationException.campaignDeleted();
        }

        campaignCacheEvictor.evict(campaign.getShortCode());

        int cancelled = applicationService.cancelAllByCampaignDeletion(campaign);
        stockRepository.remove(campaignId);

        log.info("campaign deleted: campaignId={}, ownerId={}, cancelledApplications={}",
                campaignId, userId, cancelled);
    }

    private Campaign findManageableCampaign(Long campaignId, Long userId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(CampaignApplicationException::campaignNotFound);
        if (campaign.isDeleted()) {
            throw CampaignApplicationException.campaignDeleted();
        }
        if (!campaign.isOwnedBy(userId)) {
            throw CampaignApplicationException.notOwner();
        }
        return campaign;
    }

    private String generateShortCode() {
        for (int attempt = 0; attempt < SHORT_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = shortCodeGenerator.generate();
            if (!campaignRepository.existsByShortCode(candidate)) {
                return candidate;
            }
            log.warn("short code collision: candidate={}, attempt={}", candidate, attempt + 1);
        }
        throw CampaignApplicationException.shortCodeGenerationFailed();
    }
}
