package kr.givemeticket.api.campaign.application;

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

    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getOwnedCampaigns(Long ownerId) {
        return campaignRepository.findAllOwnedBy(ownerId).stream()
                .map(campaign -> CampaignSummaryResponse.of(campaign, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getParticipatedCampaigns(Long userId) {
        Map<Long, ApplicationStatus> statusByCampaign = applicationRepository
                .findAllByUserIdAndStatusIn(userId, ApplicationStatus.active()).stream()
                .collect(Collectors.toMap(Application::getCampaignId, Application::getStatus));

        return campaignRepository.findAllByIdIn(statusByCampaign.keySet()).stream()
                .map(campaign -> CampaignSummaryResponse.of(
                        campaign, statusByCampaign.get(campaign.getId())))
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

        return CampaignResponse.of(campaign);
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
