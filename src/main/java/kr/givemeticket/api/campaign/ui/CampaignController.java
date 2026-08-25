package kr.givemeticket.api.campaign.ui;

import jakarta.validation.Valid;
import java.net.URI;
import kr.givemeticket.api.campaign.application.CampaignService;
import kr.givemeticket.api.campaign.application.dto.CampaignScope;
import kr.givemeticket.api.campaign.ui.apiSpec.CampaignApiSpec;
import kr.givemeticket.api.campaign.ui.dto.request.PatchCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.response.CreateCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignStockResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignsResponse;
import kr.givemeticket.api.campaign.ui.dto.response.PatchCampaignResponse;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CampaignController implements CampaignApiSpec {

    private final CampaignService campaignService;

    @Override
    @BusinessLogging("캠페인 생성")
    @PostMapping("campaigns")
    public ResponseEntity<CreateCampaignResponse> createCampaign(
            @LoginUserId Long userId,
            @Valid @RequestBody PostCampaignRequest request
    ) {
        CreateCampaignResponse createCampaignResponse = CreateCampaignResponse.from(
                campaignService.createCampaign(userId, request.toCampaignCreateRequest()));

        return ResponseEntity.created(URI.create("campaigns/" + createCampaignResponse.shortCode()))
                .body(createCampaignResponse);
    }

    @Override
    @GetMapping("campaigns/{shortCode}")
    public ResponseEntity<GetCampaignResponse> readCampaign(
            @LoginUserId(required = false) Long userId,
            @PathVariable("shortCode") String shortCode
    ) {
        GetCampaignResponse getCampaignResponse = GetCampaignResponse.from(
                campaignService.getCampaignDetail(shortCode, userId));

        return ResponseEntity.ok(getCampaignResponse);
    }

    @Override
    @GetMapping("campaigns/{campaignId}/stock")
    public ResponseEntity<GetCampaignStockResponse> readCampaignStock(
            @PathVariable("campaignId") Long campaignId
    ) {
        return ResponseEntity.ok(
                GetCampaignStockResponse.from(campaignService.getStock(campaignId)));
    }

    @Override
    @GetMapping("campaigns")
    public ResponseEntity<GetCampaignsResponse> readCampaigns(
            @LoginUserId Long userId,
            @RequestParam("scope") String scope
    ) {
        CampaignScope campaignScope = CampaignScope.from(scope);

        GetCampaignsResponse getCampaignsResponse = GetCampaignsResponse.from(
                switch (campaignScope) {
                    case OWNED -> campaignService.getOwnedCampaigns(userId);
                    case PARTICIPATED -> campaignService.getParticipatedCampaigns(userId);
                });

        return ResponseEntity.ok(getCampaignsResponse);
    }

    @Override
    @BusinessLogging("캠페인 수정")
    @PatchMapping("campaigns/{campaignId}")
    public ResponseEntity<PatchCampaignResponse> updateCampaign(
            @LoginUserId Long userId,
            @PathVariable("campaignId") Long campaignId,
            @Valid @RequestBody PatchCampaignRequest request
    ) {
        PatchCampaignResponse patchCampaignResponse = PatchCampaignResponse.from(
                campaignService.updateCampaign(campaignId, userId, request.toCampaignUpdateRequest()));

        return ResponseEntity.ok(patchCampaignResponse);
    }

    @Override
    @BusinessLogging("캠페인 삭제")
    @DeleteMapping("campaigns/{campaignId}")
    public ResponseEntity<Void> deleteCampaign(
            @LoginUserId Long userId,
            @PathVariable("campaignId") Long campaignId
    ) {
        campaignService.deleteCampaign(campaignId, userId);

        return ResponseEntity.noContent().build();
    }
}
