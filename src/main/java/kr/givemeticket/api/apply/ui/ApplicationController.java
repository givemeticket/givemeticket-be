package kr.givemeticket.api.apply.ui;

import java.net.URI;
import kr.givemeticket.api.apply.application.ApplicationService;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.ui.apiSpec.ApplicationApiSpec;
import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.apply.ui.dto.response.CancelApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.ConfirmApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetApplicationResponse;
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
    @BusinessLogging("신청 확정(결제)")
    @PostMapping("applications/{applicationId}/confirm")
    public ResponseEntity<ConfirmApplicationResponse> confirmApplication(
            @LoginUserId Long userId,
            @PathVariable("applicationId") Long applicationId
    ) {
        ApplicationResponse response = applicationService.confirm(applicationId, userId);
        ConfirmApplicationResponse confirmApplicationResponse =
                ConfirmApplicationResponse.from(response);

        if (response.isPending()) {
            return ResponseEntity.accepted().body(confirmApplicationResponse);
        }
        return ResponseEntity.ok(confirmApplicationResponse);
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
}
