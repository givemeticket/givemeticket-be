package kr.givemeticket.api.campaign.domain;

import java.util.Optional;

/**
 * 상세 조회가 매 요청 DB 를 치지 않도록 캠페인 값을 얹어 두는 자리.
 *
 * <p>캐시는 거들 뿐이라 실패해도 조회는 성공해야 한다. 구현체는 Redis 가 죽거나 값이 깨졌을 때
 * 예외를 던지지 말고 캐시 미스처럼 굴어야 한다.
 */
public interface CampaignCacheRepository {

    Optional<CampaignSnapshot> find(String shortCode);

    void save(CampaignSnapshot snapshot);

    void evict(String shortCode);
}
