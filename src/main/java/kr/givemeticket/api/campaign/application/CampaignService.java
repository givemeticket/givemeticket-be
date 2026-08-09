package kr.givemeticket.api.campaign.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.campaign.application.dto.request.CampaignCreateRequest;
import kr.givemeticket.api.campaign.application.dto.request.CampaignUpdateRequest;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
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
    private final ApplicationRepository applicationRepository;
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
                request.requiresPayment());
        Campaign saved = campaignRepository.save(campaign);

        stockRepository.initialize(saved.getId(), saved.getTotalStock());

        return CampaignResponse.of(saved, (long) saved.getTotalStock());
    }

    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaignDetail(String shortCode, Long userId) {
        Campaign campaign = campaignRepository.findByShortCode(shortCode)
                .orElseThrow(CampaignApplicationException::campaignNotFound);
        if (campaign.isDeleted()) {
            throw CampaignApplicationException.campaignDeleted();
        }

        Long remaining = stockRepository.getRemaining(campaign.getId());

        if (userId == null) {
            return CampaignDetailResponse.of(campaign, remaining, ViewerRole.GUEST, null, null);
        }

        Application mine = applicationRepository
                .findByCampaignIdAndUserId(campaign.getId(), userId)
                .orElse(null);

        if (campaign.isOwnedBy(userId)) {
            long confirmedCount = applicationRepository
                    .countByCampaignIdAndStatusIn(campaign.getId(), CONFIRMED_ONLY);
            return CampaignDetailResponse.of(campaign, remaining, ViewerRole.OWNER, mine, confirmedCount);
        }

        ViewerRole role = (mine != null && mine.isActive()) ? ViewerRole.PARTICIPANT : ViewerRole.VIEWER;
        return CampaignDetailResponse.of(campaign, remaining, role, mine, null);
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

        return CampaignResponse.of(campaign, stockRepository.getRemaining(campaignId));
    }

    @Transactional
    public void deleteCampaign(Long campaignId, Long userId) {
        Campaign campaign = findManageableCampaign(campaignId, userId);

        if (applicationRepository.existsByCampaignIdAndStatusIn(campaignId, ApplicationStatus.active())) {
            throw CampaignApplicationException.deleteNotAllowed();
        }

        campaign.delete();
        stockRepository.remove(campaignId);
        campaignStateRepository.remove(campaignId);

        log.info("campaign deleted: campaignId={}, ownerId={}", campaignId, userId);
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
