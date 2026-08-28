package kr.givemeticket.api.admin.ui;

import kr.givemeticket.api.admin.application.AdminAccessGuard;
import kr.givemeticket.api.admin.ui.dto.response.DeadLetterListResponse;
import kr.givemeticket.api.admin.ui.dto.response.DeadLetterResponse;
import kr.givemeticket.api.admin.ui.dto.response.RequeueResponse;
import kr.givemeticket.api.apply.domain.DeadLetterQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DLQ 를 들여다보고 다시 밀어 넣는다. {@code X-Admin-Key} 헤더가 필요하다.
 *
 * <p>재인입은 <b>원인을 고친 뒤</b>에 해야 한다. 그대로 넣으면 같은 이유로 다시 실패한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("admin/reservations/dlq")
public class DeadLetterAdminController {

    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    private final AdminAccessGuard adminAccessGuard;
    private final DeadLetterQueue deadLetterQueue;

    /** 격리된 메시지를 오래된 것부터 훑는다. */
    @GetMapping
    public ResponseEntity<DeadLetterListResponse> list(
            @RequestHeader(value = ADMIN_KEY_HEADER, required = false) String adminKey,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        adminAccessGuard.verify(adminKey);

        return ResponseEntity.ok(new DeadLetterListResponse(
                deadLetterQueue.size(),
                deadLetterQueue.peek(Math.clamp(limit, 1, 200)).stream()
                        .map(DeadLetterResponse::from)
                        .toList()));
    }

    /** 메인 큐로 되돌린다. 재시도 횟수는 0으로 초기화된다. */
    @PostMapping("{deadLetterId}/requeue")
    public ResponseEntity<RequeueResponse> requeue(
            @RequestHeader(value = ADMIN_KEY_HEADER, required = false) String adminKey,
            @PathVariable("deadLetterId") String deadLetterId
    ) {
        adminAccessGuard.verify(adminKey);

        boolean requeued = deadLetterQueue.requeue(deadLetterId);
        return requeued
                ? ResponseEntity.ok(new RequeueResponse(deadLetterId, true))
                : ResponseEntity.status(404).body(new RequeueResponse(deadLetterId, false));
    }
}
