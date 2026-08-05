package kr.givemeticket.api.campaign.application;

import kr.givemeticket.api.campaign.application.dto.request.CampaignCreateRequest;
import kr.givemeticket.api.campaign.application.dto.response.CampaignResponse;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final StockRepository stockRepository;

    @Transactional
    public CampaignResponse createCampaign(CampaignCreateRequest request) {
        Campaign campaign = new Campaign(
                request.title(), CampaignType.TICKET, request.totalStock(), request.openAt());
        Campaign saved = campaignRepository.save(campaign);

        stockRepository.initialize(saved.getId(), saved.getTotalStock());

        return CampaignResponse.of(saved, (long) saved.getTotalStock());
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(CampaignApplicationException::campaignNotFound);

        Long remaining = stockRepository.getRemaining(campaignId);

        return CampaignResponse.of(campaign, remaining);
    }
}
