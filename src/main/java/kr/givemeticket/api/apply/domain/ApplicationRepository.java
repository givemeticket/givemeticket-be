package kr.givemeticket.api.apply.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository {

    /**
     * 새 예매를 저장한다. id 는 호출자가 채워서 넘긴다.
     *
     * <p>{@code save} 가 아닌 이유는 <b>갱신을 하지 않기</b> 때문이다. id 가 있는 엔티티를
     * Spring Data 의 {@code save} 에 넘기면 merge 로 빠져 INSERT 앞에 SELECT 가 붙는다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 같은 id 나
     *         같은 (campaign, user) 조합이 이미 있는 경우
     */
    Application create(Application application);

    /** 저장된 예매 중 가장 큰 id. 없으면 0. 채번 카운터 시딩에만 쓴다. */
    long findMaxId();

    Optional<Application> findById(Long applicationId);

    Optional<Application> findByCampaignIdAndUserId(Long campaignId, Long userId);

    List<Application> findAllByUserIdAndStatusIn(Long userId, Collection<ApplicationStatus> statuses);

    /**
     * 상태가 statuses 에 있거나, 취소 사유가 failureReasons 에 있는 신청.
     *
     * <p>"나의 티켓" 목록이 쓴다. 자리를 잡고 있는 신청뿐 아니라, 사용자가 직접 누르지 않았는데
     * 취소된 신청까지 함께 보여줘야 하기 때문이다. 어떤 사유를 남길지는 호출자가 정한다.
     */
    List<Application> findAllByUserIdAndStatusInOrFailureReasonIn(
            Long userId,
            Collection<ApplicationStatus> statuses,
            Collection<FailureReason> failureReasons);

    long countByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses);

    /**
     * 한 캠페인의 신청을 신청 시각 오름차순으로 가져온다. 순서가 곧 선착순이라
     * 주최자의 신청자 목록이 이 순서를 그대로 쓴다.
     *
     * <p>행 수는 정원으로 상한이 잡혀 페이징을 두지 않았다.
     */
    List<Application> findAllByCampaignIdAndStatusIn(
            Long campaignId, Collection<ApplicationStatus> statuses);

    /**
     * 확정된 신청만 취소한다.
     *
     * @return 실제로 바뀐 행 수. 0이면 그 사이 다른 요청이 이미 취소한 것이므로
     *         호출자는 재고를 건드리면 안 된다. 이 반환값이 재고 복원의 exactly-once를 보장한다.
     */
    int cancelIfConfirmed(Long applicationId);

    /**
     * 사용자가 직접 누르지 않은 취소. 주어진 상태 중 하나일 때만 전이하며 이유를 함께 남긴다.
     */
    int cancelWithReason(Long applicationId, Collection<ApplicationStatus> statuses, FailureReason reason);
}
