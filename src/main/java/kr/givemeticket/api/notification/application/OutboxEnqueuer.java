package kr.givemeticket.api.notification.application;

import java.util.function.Supplier;
import kr.givemeticket.api.notification.domain.NotificationOutbox;
import kr.givemeticket.api.notification.domain.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원본을 <b>새 트랜잭션</b>에서 남긴다. 커밋이 끝난 뒤(AFTER_COMMIT)에 부르는 쪽을 위한 것이다.
 *
 * <p>AFTER_COMMIT 시점에는 끝난 트랜잭션의 자원이 아직 스레드에 묶여 있어서, 그냥 저장하면
 * 끝난 트랜잭션에 합류하려다 "no transaction is in progress" 로 실패한다.
 *
 * <p>리스너와 다른 빈인 것도 이유가 있다. 실패가 이 메서드 밖으로 던져지면서 새 트랜잭션은 여기서
 * 롤백으로 끝나고, 리스너는 예외만 받아 처리하면 된다. 같은 메서드 안에서 잡으면 트랜잭션이
 * 롤백 표시된 채로 커밋을 시도해 다른 예외가 난다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEnqueuer {

    private final NotificationOutboxRepository outboxRepository;

    /**
     * 원본을 만드는 일(행사·주최자 조회)도 새 트랜잭션 안에서 한다. 이미 있는 키면 만들지도 않는다.
     *
     * @return 새로 남겼으면 true. 같은 dedupe_key 의 원본이 이미 있으면 false
     * @throws org.springframework.dao.DataIntegrityViolationException 그 사이 다른 곳이 같은 키로
     *         먼저 넣은 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enqueueOnce(String dedupeKey, Supplier<NotificationOutbox> outbox) {
        if (outboxRepository.existsByDedupeKey(dedupeKey)) {
            return false;
        }
        outboxRepository.save(outbox.get());
        return true;
    }
}
