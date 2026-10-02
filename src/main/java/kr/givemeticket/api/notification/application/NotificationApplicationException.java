package kr.givemeticket.api.notification.application;

import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class NotificationApplicationException extends BusinessException {

    private NotificationApplicationException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    /** 남의 알림도 같은 404 로 끊는다. 있는지 없는지조차 알려주지 않는다. */
    public static NotificationApplicationException notFound() {
        return new NotificationApplicationException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                "알림을 찾을 수 없습니다.");
    }

    public static NotificationApplicationException invalidPageSize(int max) {
        return new NotificationApplicationException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
                "size 는 1 이상 " + max + " 이하여야 합니다.");
    }
}
