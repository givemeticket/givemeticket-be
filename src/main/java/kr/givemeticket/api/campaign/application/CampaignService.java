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
import kr.givemeticket.api.campaign.application.dto.CampaignDetailCommand;
import kr.givemeticket.api.campaign.application.dto.request.CampaignCreateRequest;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignSummaryResponse;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignCacheRepository;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignSnapshot;
import kr.givemeticket.api.campaign.domain.CampaignState;
import kr.givemeticket.api.campaign.domain.CampaignStateRepository;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.ShortCodeGenerator;
import kr.givemeticket.api.campaign.domain.StockRepository;
import kr.givemeticket.api.campaign.domain.ViewerRole;
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
    private final CampaignCacheRepository campaignCacheRepository;
    private final CampaignCacheEvictor campaignCacheEvictor;
    private final ShortCodeGenerator shortCodeGenerator;

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

        return CampaignResponse.of(saved, (long) saved.getTotalStock());
    }

    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaignDetail(String shortCode, Long userId) {
        CampaignSnapshot campaign = findCachedCampaign(shortCode);
        if (campaign.isDeleted()) {
            throw CampaignApplicationException.campaignDeleted();
        }

        Long remaining = stockRepository.getRemaining(campaign.id());

        if (userId == null) {
            return CampaignDetailResponse.of(campaign, remaining, ViewerRole.GUEST, null, null);
        }

        Application mine = applicationRepository
                .findByCampaignIdAndUserId(campaign.id(), userId)
                .orElse(null);

        if (campaign.isOwnedBy(userId)) {
            long confirmedCount = applicationRepository
                    .countByCampaignIdAndStatusIn(campaign.id(), CONFIRMED_ONLY);
            return CampaignDetailResponse.of(campaign, remaining, ViewerRole.OWNER, mine, confirmedCount);
        }

        ViewerRole role = (mine != null && mine.isActive()) ? ViewerRole.PARTICIPANT : ViewerRole.VIEWER;
        return CampaignDetailResponse.of(campaign, remaining, role, mine, null);
    }

    /**
     * 캐시를 먼저 보고, 없으면 DB 에서 읽어 채운다.
     *
     * <p>끝난 행사는 캐시에 올리지 않는다({@link CampaignSnapshot#isCacheable}).
     * 다시 몰려서 조회될 일이 없는데 Redis 메모리만 차지하기 때문이다.
     */
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

    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getOwnedCampaigns(Long ownerId) {
        List<Campaign> campaigns = campaignRepository.findAllOwnedBy(ownerId);
        Map<Long, Long> remaining = stockRepository.getRemaining(idsOf(campaigns));

        return campaigns.stream()
                .map(campaign -> CampaignSummaryResponse.of(
                        campaign, remaining.get(campaign.getId()), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getParticipatedCampaigns(Long userId) {
        Map<Long, ApplicationStatus> statusByCampaign = applicationRepository
                .findAllByUserIdAndStatusIn(userId, ApplicationStatus.active()).stream()
                .collect(Collectors.toMap(Application::getCampaignId, Application::getStatus));

        List<Campaign> campaigns = campaignRepository.findAllByIdIn(statusByCampaign.keySet());
        Map<Long, Long> remaining = stockRepository.getRemaining(idsOf(campaigns));

        return campaigns.stream()
                .map(campaign -> CampaignSummaryResponse.of(
                        campaign,
                        remaining.get(campaign.getId()),
                        statusByCampaign.get(campaign.getId())))
                .toList();
    }

    @Transactional
    public CampaignResponse updateCampaign(Long campaignId, Long userId, CampaignUpdateRequest request) {
        if (request.isEmpty()) {
            throw CampaignApplicationException.nothingToUpdate();
        }
        Campaign campaign = findManageableCampaign(campaignId, userId);

        if (request.openAt() != null) {
            if (!campaign.isScheduled() || !request.openAt().isAfter(campaign.getOpenAt())) {
                throw CampaignApplicationException.openAtNotDelayable();
            }
            campaign.changeOpenAt(request.openAt());
        }

        if (request.totalStock() != null) {
            if (request.totalStock() <= campaign.getTotalStock()) {
                throw CampaignApplicationException.totalStockNotIncreasable();
            }
            int delta = campaign.changeTotalStock(request.totalStock());
            stockRepository.increaseBy(campaignId, delta);

            campaignStateRepository.find(campaignId).ifPresent(state ->
                    campaignStateRepository.save(campaignId,
                            new CampaignState(state.requiresPayment(), campaign.getTotalStock())));

            log.info("campaign stock increased: campaignId={}, delta={}, totalStock={}",
                    campaignId, delta, campaign.getTotalStock());
        }

        if (request.detail() != null) {
            campaign.changeDetail(request.detail().toCampaignDetail());
        }

        campaignCacheEvictor.evict(campaign.getShortCode());

        return CampaignResponse.of(campaign, stockRepository.getRemaining(campaignId));
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
        campaignCacheEvictor.evict(campaign.getShortCode());

        if (campaignPersister.markDeleted(campaignId) == 0) {
            // 그 사이 다른 요청이 이미 지웠다. 취소·환불을 두 번 돌리지 않는다.
            throw CampaignApplicationException.campaignDeleted();
        }

        // markDeleted 직전에 들어온 조회가 살아 있는 값으로 캐시를 다시 채웠을 수 있다.
        // 지우지 않으면 삭제된 캠페인이 TTL 만큼 조회된다.
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

    private List<Long> idsOf(List<Campaign> campaigns) {
        return campaigns.stream().map(Campaign::getId).toList();
    }
}
