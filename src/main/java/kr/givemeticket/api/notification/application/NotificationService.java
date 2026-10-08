package kr.givemeticket.api.notification.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.givemeticket.api.notification.application.dto.response.NotificationPageResponse;
import kr.givemeticket.api.notification.application.dto.response.NotificationResponse;
import kr.givemeticket.api.notification.domain.Notification;
import kr.givemeticket.api.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림함. 누구에게 보낼지는 알림을 만드는 쪽(팬아웃)이 이미 정해 두었으므로,
 * 여기서는 토큰의 userId 로 꺼내기만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    /** 배지는 "99+" 까지만 그린다. 그 이상은 세어 봐야 화면이 같다. */
    public static final int UNREAD_COUNT_CAP = 100;

    private final NotificationRepository notificationRepository;

    /**
     * 한 건 더 읽어서 다음 페이지가 있는지 본다. 전체 개수를 세는 쿼리가 따로 돌지 않는다.
     *
     * @param cursor 이전 응답의 nextCursor. 첫 페이지면 null
     * @param size   null 이면 기본값
     */
    public NotificationPageResponse getNotifications(Long userId, Long cursor, Integer size) {
        int pageSize = (size == null) ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw NotificationApplicationException.invalidPageSize(MAX_PAGE_SIZE);
        }

        List<Notification> rows = notificationRepository.findPage(userId, cursor, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<Notification> page = hasNext ? rows.subList(0, pageSize) : rows;

        return new NotificationPageResponse(
                page.stream().map(NotificationResponse::from).toList(),
                hasNext ? page.get(page.size() - 1).getId() : null);
    }

    public int countUnread(Long userId) {
        return notificationRepository.countUnread(userId, UNREAD_COUNT_CAP);
    }

    /** 이미 읽은 알림을 다시 눌러도 오류가 아니다. 처음 읽은 시각이 그대로 남는다. */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(NotificationApplicationException::notFound);

        notification.markRead(LocalDateTime.now());
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    /** 탈퇴 정리. 받을 사람이 없어진 알림은 남겨 둘 이유가 없다. */
    @Transactional
    public void deleteAllOf(Long userId) {
        int deleted = notificationRepository.deleteAllByUserId(userId);
        if (deleted > 0) {
            log.info("notifications deleted by user withdrawal: userId={}, count={}", userId, deleted);
        }
    }
}
