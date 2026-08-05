package kr.givemeticket.api.campaign.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import kr.givemeticket.api.campaign.ui.dto.response.CreateCampaignResponse;
import kr.givemeticket.api.campaign.ui.dto.response.GetCampaignResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "캠페인 API", description = "선착순 티켓 캠페인 관련 API 명세입니다.")
public interface CampaignApiSpec {

    @Operation(summary = "캠페인 등록", description = "캠페인을 등록하고 잔여 재고를 초기화합니다. openAt은 UTC 기준 미래 시각이어야 합니다.")
    ResponseEntity<CreateCampaignResponse> createCampaign(@Valid @RequestBody PostCampaignRequest request);

    @Operation(summary = "캠페인 조회", description = "캠페인 정보와 잔여 재고를 조회합니다.")
    ResponseEntity<GetCampaignResponse> readCampaign(
            @Parameter(description = "캠페인 ID", example = "1")
            @PathVariable("campaignId") Long campaignId
    );
}
