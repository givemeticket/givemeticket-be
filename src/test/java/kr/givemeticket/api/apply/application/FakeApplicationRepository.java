package kr.givemeticket.api.apply.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

/**
 * 저장된 행을 id 로 들고 있는다. 워커의 멱등성과 apply 의 번호 선택이 모두
 * "그 id 의 행이 있는가"에 걸려 있어서, 그 질문에만 답하면 된다.
 */
class FakeApplicationRepository implements ApplicationRepository {

    final Map<Long, Application> rows = new LinkedHashMap<>();
    final List<Application> created = new ArrayList<>();

    void put(Application application) {
        rows.put(application.getId(), application);
    }

    @Override
    public Application create(Application application) {
        if (rows.containsKey(application.getId())) {
            throw new IllegalStateException("duplicate id: " + application.getId());
        }
        rows.put(application.getId(), application);
        created.add(application);
        return application;
    }

    @Override
    public Optional<Application> findById(Long applicationId) {
        return Optional.ofNullable(rows.get(applicationId));
    }

    @Override
    public Optional<Application> findByCampaignIdAndUserId(Long campaignId, Long userId) {
        return rows.values().stream()
                .filter(a -> a.getCampaignId().equals(campaignId) && a.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public long findMaxId() {
        return rows.keySet().stream().mapToLong(Long::longValue).max().orElse(0L);
    }

    @Override
    public List<Application> findAllByUserIdAndStatusIn(
            Long userId, Collection<ApplicationStatus> statuses) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Application> findAllByUserIdAndStatusInOrFailureReasonIn(
            Long userId, Collection<ApplicationStatus> statuses,
            Collection<FailureReason> failureReasons) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countByCampaignIdAndStatusIn(
            Long campaignId, Collection<ApplicationStatus> statuses) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Application> findAllByCampaignIdAndStatusIn(
            Long campaignId, Collection<ApplicationStatus> statuses) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int cancelIfConfirmed(Long applicationId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int cancelWithReason(Long applicationId, Collection<ApplicationStatus> statuses,
                                FailureReason reason) {
        throw new UnsupportedOperationException();
    }
}
