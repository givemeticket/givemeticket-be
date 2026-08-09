package kr.givemeticket.api.apply.ui;

import java.net.URI;
import kr.givemeticket.api.apply.application.ApplicationService;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.ui.apiSpec.ApplicationApiSpec;
import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetApplicationResponse;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.global.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            @CurrentUserId Long userId,
            @PathVariable("campaignId") Long campaignId
    ) {
        ApplicationResponse response = applicationService.apply(campaignId, userId);
        ApplyResponse applyResponse = ApplyResponse.from(response);

        // 결제 결과를 모르는 건은 아직 확정이 아니다. 202로 내려보내 클라이언트가 폴링하게 한다.
        if (response.isPending()) {
            return ResponseEntity.accepted().body(applyResponse);
        }

        return ResponseEntity.created(URI.create("applications/" + applyResponse.id()))
                .body(applyResponse);
    }

    @Override
    @GetMapping("applications/{applicationId}")
    public ResponseEntity<GetApplicationResponse> readApplication(
            @CurrentUserId Long userId,
            @PathVariable("applicationId") Long applicationId
    ) {
        GetApplicationResponse getApplicationResponse = GetApplicationResponse.from(
                applicationService.getApplication(applicationId, userId));

        return ResponseEntity.status(HttpStatus.OK).body(getApplicationResponse);
    }
}
