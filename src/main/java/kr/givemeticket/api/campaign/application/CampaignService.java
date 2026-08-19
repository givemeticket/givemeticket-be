package kr.givemeticket.api.campaign.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.givemeticket.api.apply.application.ApplicationService;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.application.dto.CampaignDetailCommand;
import kr.givemeticket.api.campaign.application.dto.request.CampaignCreateRequest;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignStockResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSummaryResponse;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.ShortCodeGenerator;
import kr.givemeticket.api.campaign.domain.StockRepository;
import kr.givemeticket.api.campaign.domain.ViewerRole;
import kr.givemeticket.api.user.application.UserService;
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

    private final CampaignRepository campaignRepository;
    private final CampaignPersister campaignPersister;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final StockRepository stockRepository;
    private final CampaignStateRepository campaignStateRepository;
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
                request.requiresPayment(),
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

    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaignDetail(String shortCode, Long userId) {
        Campaign campaign = campaignRepository.findByShortCode(shortCode)
                .orElseThrow(CampaignApplicationException::campaignNotFound);
        if (campaign.isDeleted()) {
            throw CampaignApplicationException.campaignDeleted();
        }

        if (userId == null) {
            return CampaignDetailResponse.of(campaign, ViewerRole.GUEST, null, null);
        }

        Application mine = applicationRepository
                .findByCampaignIdAndUserId(campaign.getId(), userId)
                .orElse(null);

        if (campaign.isOwnedBy(userId)) {
            long confirmedCount = applicationRepository
                    .countByCampaignIdAndStatusIn(campaign.getId(), CONFIRMED_ONLY);
            return CampaignDetailResponse.of(campaign, ViewerRole.OWNER, mine, confirmedCount);
        }

        ViewerRole role = (mine != null && mine.isActive()) ? ViewerRole.PARTICIPANT : ViewerRole.VIEWER;
        return CampaignDetailResponse.of(campaign, role, mine, null);
    }

    /**
     * 삭제한 행사도 함께 내려간다. 목록에서 조용히 사라지면 개설자는 지운 것인지
     * 사라진 것인지 알 수 없다. status=DELETED 로 "삭제됨"이라고 보여주면 된다.
     */
    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getOwnedCampaigns(Long ownerId) {
        return toSummaries(campaignRepository.findAllOwnedBy(ownerId), Map.of());
    }

    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getParticipatedCampaigns(Long userId) {
        Map<Long, ApplicationStatus> statusByCampaign = applicationRepository
                .findAllByUserIdAndStatusIn(userId, ApplicationStatus.active()).stream()
                .collect(Collectors.toMap(Application::getCampaignId, Application::getStatus));

        return toSummaries(campaignRepository.findAllByIdIn(statusByCampaign.keySet()), statusByCampaign);
    }

    /**
     * 개설자 닉네임은 캠페인마다 조회하지 않고 한 번에 모아 온다.
     */
    private List<CampaignSummaryResponse> toSummaries(
            List<Campaign> campaigns,
            Map<Long, ApplicationStatus> statusByCampaign
    ) {
        Map<Long, String> nicknameByOwner = userService.findNicknames(
                campaigns.stream().map(Campaign::getOwnerId).collect(Collectors.toSet()));

        return campaigns.stream()
                .map(campaign -> CampaignSummaryResponse.of(
                        campaign,
                        nicknameByOwner.get(campaign.getOwnerId()),
                        statusByCampaign.get(campaign.getId())))
                .toList();
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

        return CampaignResponse.of(campaign);
    }

    /**
     * 열린 행사는 뒤로 미루는 것만 된다. 앞당기면 이미 신청을 놓친 사람이 생긴다.
     *
     * <p>미룰 때는 신청 게이트를 걷어 접수를 멈춘다. 이걸 두고 시각만 바꾸면
     * "아직 안 열린 행사인데 신청은 받는" 상태가 된다. 새 시각이 되면 스케줄러가 다시 연다.
     */
    private void changeOpenAt(Campaign campaign, LocalDateTime openAt, boolean opened) {
        if (!opened) {
            campaign.changeOpenAt(openAt);
            return;
        }
        if (!openAt.isAfter(campaign.getOpenAt())) {
            throw CampaignApplicationException.openAtNotDelayable();
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
                campaignStateRepository.save(campaignId,
                        new CampaignState(state.requiresPayment(), totalStock)));

        log.info("campaign stock changed: campaignId={}, delta={}, totalStock={}",
                campaignId, delta, totalStock);
    }

    /**
     * 신청자가 있어도 삭제할 수 있다. 남은 신청은 전부 취소되고, 결제된 건은 환불된다.
     *
     * <p>환불이 신청 수만큼 외부 호출을 일으키므로 트랜잭션으로 감싸지 않는다.
     * 상태 전이는 {@link CampaignPersister}·{@link ApplicationPersister} 가 건별로 짧게 끊는다.
     */
    public void deleteCampaign(Long campaignId, Long userId) {
        Campaign campaign = findManageableCampaign(campaignId, userId);

        // 신규 신청을 먼저 막는다. 이걸 뒤로 미루면 취소하는 사이에 들어온 신청이 살아남는다.
        campaignStateRepository.remove(campaignId);

        if (campaignPersister.markDeleted(campaignId) == 0) {
            // 그 사이 다른 요청이 이미 지웠다. 취소·환불을 두 번 돌리지 않는다.
            throw CampaignApplicationException.campaignDeleted();
        }

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
