package kr.givemeticket.api.apply.ui.apiSpec;

import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.apply.ui.dto.response.ConfirmApplicationResponse;
import kr.givemeticket.api.apply.ui.dto.response.GetApplicationResponse;
import kr.givemeticket.api.global.web.CurrentUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

public interface ApplicationApiSpec {

    ResponseEntity<ApplyResponse> apply(
            @CurrentUserId Long userId,
            @PathVariable("campaignId") Long campaignId
    );

    ResponseEntity<GetApplicationResponse> readApplication(
            @CurrentUserId Long userId,
            @PathVariable("applicationId") Long applicationId
    );

    ResponseEntity<ConfirmApplicationResponse> confirmApplication(
            @CurrentUserId Long userId,
            @PathVariable("applicationId") Long applicationId
    );
}
