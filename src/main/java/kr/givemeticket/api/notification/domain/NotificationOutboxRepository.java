package kr.givemeticket.api.notification.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationOutboxRepository {

    NotificationOutbox save(NotificationOutbox outbox);

    boolean existsByDedupeKey(String dedupeKey);

    /**
     * 아직 펼치지 않은 원본 중 가장 오래된 하나를 잠그고 가져온다. 다른 파드의 워커가 이미 잡고 있는
     * 행은 건너뛴다. 트랜잭션 안에서 불러야 하고, 잠금은 그 트랜잭션이 끝날 때 풀린다.
     */
    Optional<NotificationOutbox> claimNext();

    /** 처리한 지 오래된 원본을 최대 {@code limit} 건 지운다. */
    int deleteProcessedBefore(LocalDateTime before, int limit);
}
