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

    /**
     * 공유 링크로 들어오는 상세 화면. {@code userId}가 null이면 비로그인 조회다.
     */
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

        // 종결된 신청(FAILED 등)도 함께 내려준다. "결제 실패" 같은 직전 결과를 화면에 띄워야 한다.
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

    /**
     * 오픈 지연과 정원 증원만 허용한다. 앞당기기·감원은 이미 링크를 받은 사람의 기대를 깨뜨린다.
     */
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

            // 이미 열려 있으면 신청 핫패스가 보는 메타도 같이 갱신한다.
            // 매진 상태였다면 잔여 재고가 0을 넘는 순간 자동으로 다시 신청 가능해진다.
            // (FULL을 저장하지 않고 파생시키기 때문에 별도의 '재오픈' 처리가 필요 없다)
            campaignStateRepository.find(campaignId).ifPresent(state ->
                    campaignStateRepository.open(campaignId,
                            new CampaignState(state.requiresPayment(), campaign.getTotalStock())));

            log.info("campaign stock increased: campaignId={}, delta={}, totalStock={}",
                    campaignId, delta, campaign.getTotalStock());
        }

        return CampaignResponse.of(campaign, stockRepository.getRemaining(campaignId));
    }

    @Transactional
    public void deleteCampaign(Long campaignId, Long userId) {
        Campaign campaign = findManageableCampaign(campaignId, userId);

        // PENDING·UNKNOWN까지 막는다. 결제가 진행 중이거나 결과를 모르는 건이 남은 채로
        // 캠페인을 지우면 그 돈을 어떻게 처리할지 판단할 근거가 사라진다.
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

    /**
     * 난수라 충돌은 사실상 없지만, 충돌하면 유니크 제약 위반으로 요청 전체가 죽으므로 미리 확인한다.
     */
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
