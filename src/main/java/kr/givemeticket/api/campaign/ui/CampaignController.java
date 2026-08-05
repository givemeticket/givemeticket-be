package kr.givemeticket.api.campaign.ui;

import jakarta.validation.Valid;
import java.net.URI;
import kr.givemeticket.api.campaign.application.CampaignService;
import kr.givemeticket.api.campaign.ui.apiSpec.CampaignApiSpec;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.response.CreateCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CampaignController implements CampaignApiSpec {

    private final CampaignService campaignService;

    @Override
    @PostMapping("campaigns")
    public ResponseEntity<CreateCampaignResponse> createCampaign(
            @Valid @RequestBody PostCampaignRequest request
    ) {
        CreateCampaignResponse createCampaignResponse = CreateCampaignResponse.from(
                campaignService.createCampaign(request.toCampaignCreateRequest()));

        return ResponseEntity.created(URI.create("campaigns/" + createCampaignResponse.id()))
                .body(createCampaignResponse);
    }

    @Override
    @GetMapping("campaigns/{campaignId}")
    public ResponseEntity<GetCampaignResponse> readCampaign(
            @PathVariable("campaignId") Long campaignId
    ) {
        GetCampaignResponse getCampaignResponse = GetCampaignResponse.from(
                campaignService.getCampaign(campaignId));

        return ResponseEntity.ok(getCampaignResponse);
    }
}
