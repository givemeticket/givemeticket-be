package kr.givemeticket.api.apply.infrastructure;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.apply.domain.PendingReservation;
import kr.givemeticket.api.apply.domain.PendingReservationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 저장 대기 중인 예매의 Redis 구현. 짧은 TTL 을 가진 해시다.
 *
 * <p>언제: 신청 시 기록하고, 조회가 DB 에서 행을 못 찾았을 때 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class RedisPendingReservationStore implements PendingReservationStore {

    private static final String KEY_PREFIX = "application:pending:";
    private static final String FIELD_CAMPAIGN_ID = "campaignId";
    private static final String FIELD_USER_ID = "userId";

    /** 재시도가 다 끝나기에 충분한 시간. 조회가 DB 를 먼저 보므로 넉넉해도 해가 없다. */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void put(PendingReservation reservation) {
        String key = key(reservation.applicationId());
        stringRedisTemplate.opsForHash().putAll(key, Map.of(
                FIELD_CAMPAIGN_ID, String.valueOf(reservation.campaignId()),
                FIELD_USER_ID, String.valueOf(reservation.userId())
        ));
        stringRedisTemplate.expire(key, TTL);
    }

    @Override
    public Optional<PendingReservation> find(Long applicationId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key(applicationId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PendingReservation(
                applicationId,
                Long.parseLong((String) entries.get(FIELD_CAMPAIGN_ID)),
                Long.parseLong((String) entries.get(FIELD_USER_ID))
        ));
    }

    /** 예매 번호로 만드는 키. */
    private String key(Long applicationId) {
        return KEY_PREFIX + applicationId;
    }
}
