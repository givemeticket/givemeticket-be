package kr.givemeticket.api.notification.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;

/**
 * 팬아웃이 수신자를 고르는 조회 하나만 채운다.
 */
class FakeApplicationRepository implements ApplicationRepository {

    private final List<Application> applications = new ArrayList<>();

    void put(Application application) {
        applications.add(application);
    }

    @Override
    public List<Application> findAllByCampaignIdAndStatusIn(
            Long campaignId, Collection<ApplicationStatus> statuses) {
        return applications.stream()
                .filter(application -> application.getCampaignId().equals(campaignId))
                .filter(application -> statuses.contains(application.getStatus()))
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
    public int cancelIfConfirmed(Long applicationId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int cancelWithReason(
            Long applicationId, Collection<ApplicationStatus> statuses, FailureReason reason) {
        throw new UnsupportedOperationException();
    }
}
