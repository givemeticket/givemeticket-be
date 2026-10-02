package kr.givemeticket.api.notification.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    void saveAll(Collection<Notification> notifications);

    Optional<Notification> findById(Long notificationId);

    /**
     * 내 알림을 최신순으로. id 가 {@code cursor} 보다 작은 것만 본다.
     *
     * @param cursor 첫 페이지면 null
     */
    List<Notification> findPage(Long userId, Long cursor, int limit);

    /** 안 읽은 알림 수. {@code cap} 개까지만 센다 — 배지는 "99+" 이상을 구분하지 않는다. */
    int countUnread(Long userId, int cap);

    /** @return 이번에 읽음으로 바뀐 행 수 */
    int markAllRead(Long userId, LocalDateTime now);

    /** 탈퇴한 사용자의 알림을 지운다. */
    int deleteAllByUserId(Long userId);

    /** 보관 기간이 지난 알림을 최대 {@code limit} 건 지운다. */
    int deleteCreatedBefore(LocalDateTime before, int limit);
}
