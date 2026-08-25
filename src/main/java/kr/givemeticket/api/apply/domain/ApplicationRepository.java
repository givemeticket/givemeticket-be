package kr.givemeticket.api.apply.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository {

    Application save(Application application);

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
