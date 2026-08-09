package kr.givemeticket.api.apply.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository {

    Application save(Application application);

    Optional<Application> findById(Long applicationId);

    Optional<Application> findByCampaignIdAndUserId(Long campaignId, Long userId);

    List<Application> findAllByUserIdAndStatusIn(Long userId, Collection<ApplicationStatus> statuses);

    /**
     * 홀드 시간을 넘긴 결제 대기 신청. 만료 sweeper가 회수 대상으로 쓴다.
     */
    List<Application> findExpiredPending(LocalDateTime now, int limit);

    long countByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses);

    boolean existsByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses);

    /**
     * PENDING인 신청만 확정으로 바꾼다.
     *
     * @return 실제로 바뀐 행 수. 0이면 이미 다른 주체(sweeper 등)가 전이시킨 것이므로
     *         호출자는 재고를 건드리면 안 된다. 이 반환값이 재고 복원의 exactly-once를 보장한다.
     */
    int confirmIfPending(Long applicationId, String transactionId);

    int failIfPending(Long applicationId, FailureReason reason);

    int markUnknownIfPending(Long applicationId);

    int cancelIfConfirmed(Long applicationId);
}
