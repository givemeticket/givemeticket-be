package kr.givemeticket.api.campaign.ui.apiSpec;

import jakarta.validation.Valid;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.response.CreateCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

public interface CampaignApiSpec {

    ResponseEntity<CreateCampaignResponse> createCampaign(@Valid @RequestBody PostCampaignRequest request);

    ResponseEntity<GetCampaignResponse> readCampaign(@PathVariable("campaignId") Long campaignId);
}
