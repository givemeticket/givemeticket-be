package kr.givemeticket.api.apply.ui;

import java.net.URI;
import kr.givemeticket.api.apply.application.ApplicationService;
import kr.givemeticket.api.apply.ui.apiSpec.ApplicationApiSpec;
import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.apply.ui.dto.response.CancelApplicantResponse;
import kr.givemeticket.api.apply.ui.dto.response.CancelApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetCampaignApplicantsResponse;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationController implements ApplicationApiSpec {

    private final ApplicationService applicationService;

    @Override
    @BusinessLogging("캠페인 신청")
    @PostMapping("campaigns/{campaignId}/apply")
    public ResponseEntity<ApplyResponse> apply(
            @LoginUserId Long userId,
            @PathVariable("campaignId") Long campaignId
    ) {
        ApplyResponse applyResponse = ApplyResponse.from(
                applicationService.apply(campaignId, userId));

        return ResponseEntity.created(URI.create("applications/" + applyResponse.id()))
                .body(applyResponse);
    }

    @Override
    @BusinessLogging("신청 취소")
    @PostMapping("applications/{applicationId}/cancel")
    public ResponseEntity<CancelApplicationResponse> cancelApplication(
            @LoginUserId Long userId,
            @PathVariable("applicationId") Long applicationId
    ) {
        CancelApplicationResponse cancelApplicationResponse = CancelApplicationResponse.from(
                applicationService.cancel(applicationId, userId));

        return ResponseEntity.ok(cancelApplicationResponse);
    }

    @Override
    @GetMapping("applications/{applicationId}")
    public ResponseEntity<GetApplicationResponse> readApplication(
            @LoginUserId Long userId,
            @PathVariable("applicationId") Long applicationId
    ) {
        GetApplicationResponse getApplicationResponse = GetApplicationResponse.from(
                applicationService.getApplication(applicationId, userId));

        return ResponseEntity.ok(getApplicationResponse);
    }

    @Override
    @GetMapping("campaigns/{campaignId}/applications")
    public ResponseEntity<GetCampaignApplicantsResponse> readCampaignApplicants(
            @LoginUserId Long userId,
            @PathVariable("campaignId") Long campaignId
    ) {
        return ResponseEntity.ok(GetCampaignApplicantsResponse.of(
                campaignId, applicationService.getApplicants(campaignId, userId)));
    }

    @Override
    @BusinessLogging("주최자 신청 취소")
    @PostMapping("campaigns/{campaignId}/applications/{applicationId}/cancel")
    public ResponseEntity<CancelApplicantResponse> cancelApplicantByOwner(
            @LoginUserId Long userId,
            @PathVariable("campaignId") Long campaignId,
            @PathVariable("applicationId") Long applicationId
    ) {
        return ResponseEntity.ok(CancelApplicantResponse.from(
                applicationService.cancelByOwner(campaignId, applicationId, userId)));
    }
}
