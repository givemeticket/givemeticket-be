package kr.givemeticket.api.campaign.domain;

import java.util.Collection;
import java.util.Map;

public interface StockRepository {

    void initialize(Long campaignId, int totalStock);

    /**
     * 사용자 한 명 몫의 자리를 잡는다. 중복 확인과 재고 차감이 한 원자 연산이다.
     *
     * <p>{@code userId} 를 받는 이유는 중복 판정이 이제 여기 있기 때문이다. DB 유니크
     * 제약은 최종 방어선으로 남지만, 사용자에게 답할 판정은 이 호출이 한다.
     */
    StockDecreaseResult decrease(Long campaignId, Long userId);

    /**
     * 자리를 되돌린다. {@code upperBound} 를 넘지 못하게 막아 중복 호출에도 안전하다.
     * 신청자 집합에서도 빼야 그 사용자가 다시 신청할 수 있다.
     */
    void restore(Long campaignId, Long userId, int upperBound);

    /**
     * 관리자의 정원 증원.
     */
    void increaseBy(Long campaignId, int delta);

    /**
     * 초기화된 적 없거나 삭제된 캠페인이면 null.
     */
    Long getRemaining(Long campaignId);

    /**
     * 목록 화면용 일괄 조회. 캠페인 수만큼 왕복하지 않도록 한 번에 읽는다.
     *
     * @return 재고가 있는 캠페인만 담긴다. 삭제된 캠페인은 키가 없으므로 빠진다
     */
    Map<Long, Long> getRemainingAll(Collection<Long> campaignIds);

    /** 캠페인이 사라졌다. 재고와 신청자 집합을 함께 지운다. */
    void remove(Long campaignId);
}
