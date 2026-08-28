package kr.givemeticket.api.campaign.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

/**
 * 목록 조회가 어떤 신청을 가져와야 하는지를 실제 JPQL 과 같은 규칙으로 흉내낸다.
 * 쿼리 문자열 자체는 여기서 검증되지 않지만, 서비스가 무엇을 요구하는지는 이 규칙에 고정된다.
 */
class FakeApplicationRepository implements ApplicationRepository {

    private final List<Application> applications = new ArrayList<>();

    void put(Application application) {
        applications.add(application);
    }

    @Override
    public List<Application> findAllByUserIdAndStatusIn(
            Long userId, Collection<ApplicationStatus> statuses) {
        return applications.stream()
                .filter(application -> application.getUserId().equals(userId))
                .filter(application -> statuses.contains(application.getStatus()))
                .toList();
    }

    @Override
    public List<Application> findAllByUserIdAndStatusInOrFailureReasonIn(
            Long userId,
            Collection<ApplicationStatus> statuses,
            Collection<FailureReason> failureReasons) {
        return applications.stream()
                .filter(application -> application.getUserId().equals(userId))
                // 사유가 비어 있으면 어떤 사유와도 매칭되지 않는다. SQL 의 NULL IN (...) 과 같다.
                .filter(application -> statuses.contains(application.getStatus())
                        || (application.getStatus() == ApplicationStatus.CANCELLED
                            && application.getFailureReason() != null
                            && failureReasons.contains(application.getFailureReason())))
                .toList();
    }

    @Override
    public Application create(Application application) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long findMaxId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Application> findById(Long applicationId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Application> findByCampaignIdAndUserId(Long campaignId, Long userId) {
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
    public int cancelWithReason(
            Long applicationId, Collection<ApplicationStatus> statuses, FailureReason reason) {
        throw new UnsupportedOperationException();
    }
}
