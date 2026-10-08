package kr.givemeticket.api.notification.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import kr.givemeticket.api.notification.ui.dto.response.GetNotificationsResponse;
import kr.givemeticket.api.notification.ui.dto.response.GetUnreadCountResponse;
import kr.givemeticket.api.notification.ui.dto.response.ReadAllNotificationsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "알림 API", description = "내가 신청한 행사에 생긴 일을 알려주는 알림함 API 명세입니다.")
public interface NotificationApiSpec {

    @Operation(summary = "내 알림 목록",
            description = """
                    내 알림을 최신순으로 내려줍니다. 토큰의 사용자 것만 나옵니다.

                    - 첫 페이지는 cursor 없이 부릅니다. 다음 페이지는 응답의 nextCursor 를 cursor 로 넘깁니다
                    - nextCursor 가 null 이면 마지막 페이지입니다
                    - size 는 기본 20, 최대 50 입니다. 벗어나면 400 INVALID_PAGE_SIZE
                    - type 별 문구
                      - CAMPAIGN_CHANGED: 행사 정보가 바뀌었습니다. changes 에 항목별 before/after 가 있습니다.
                        field 는 TITLE / EVENT_AT / EVENT_END_AT / LOCATION / ADDRESS 이고,
                        EVENT_AT·EVENT_END_AT 의 값은 UTC 시각 문자열(Z)입니다
                      - CAMPAIGN_DELETED: 행사가 삭제되어 신청이 취소됐습니다
                      - APPLICATION_CANCELLED: 주최자가 내 신청을 취소했습니다. 행사는 그대로 있습니다
                      - EVENT_REMINDER: 행사가 24시간 안에 시작합니다. eventAt 이 시작 일시입니다
                      - WISHED_CAMPAIGN_OPENED: 찜한 행사의 신청이 열렸습니다. 이미 신청한 사람에게는 가지 않습니다
                    - campaignTitle 은 알림이 만들어질 때의 제목입니다. 이후에 바뀌어도 그대로 남습니다
                    - 알림은 90일 동안 보관됩니다
                    """)
    ResponseEntity<GetNotificationsResponse> readNotifications(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지면 비웁니다", example = "120")
            @RequestParam(value = "cursor", required = false) Long cursor,
            @Parameter(description = "페이지 크기. 기본 20, 최대 50", example = "20")
            @RequestParam(value = "size", required = false) Integer size
    );

    @Operation(summary = "안 읽은 알림 수",
            description = """
                    헤더의 알림 배지에 씁니다. 화면에 들어올 때와 30~60초 간격으로 불러 주세요.

                    - 최대 100 까지만 셉니다. 100 이면 "99+" 로 그리면 됩니다
                    """)
    ResponseEntity<GetUnreadCountResponse> readUnreadCount(
            @Parameter(hidden = true) @LoginUserId Long userId);

    @Operation(summary = "알림 읽음 처리",
            description = """
                    알림 하나를 읽음으로 바꿉니다. 알림을 눌러 행사 상세로 이동할 때 부릅니다.

                    - 성공하면 204 이고 본문이 없습니다
                    - 이미 읽은 알림이어도 204 입니다. 처음 읽은 시각이 유지됩니다
                    - 없는 알림이거나 남의 알림이면 404 NOTIFICATION_NOT_FOUND
                    """)
    @ApiResponse(responseCode = "204", description = "읽음 처리됨. 본문 없음")
    ResponseEntity<Void> readNotification(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "알림 ID", example = "1")
            @PathVariable("notificationId") Long notificationId
    );

    @Operation(summary = "알림 모두 읽음",
            description = """
                    내 안 읽은 알림을 전부 읽음으로 바꿉니다.

                    - readCount 는 이번에 바뀐 수입니다. 이미 다 읽었으면 0 입니다
                    """)
    ResponseEntity<ReadAllNotificationsResponse> readAllNotifications(
            @Parameter(hidden = true) @LoginUserId Long userId);
}
