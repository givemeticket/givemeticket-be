package kr.givemeticket.api.apply.infrastructure;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaApplicationRepository implements ApplicationRepository {

    private final SpringDataJpaApplicationRepository springDataJpaApplicationRepository;

    @Override
    public Application save(Application application) {
        return springDataJpaApplicationRepository.save(application);
    }

    @Override
    public Optional<Application> findById(Long applicationId) {
        return springDataJpaApplicationRepository.findById(applicationId);
    }

    @Override
    public Optional<Application> findByCampaignIdAndUserId(Long campaignId, Long userId) {
        return springDataJpaApplicationRepository.findByCampaignIdAndUserId(campaignId, userId);
    }

    @Override
    public List<Application> findAllByUserIdAndStatusIn(
            Long userId, Collection<ApplicationStatus> statuses) {
        return springDataJpaApplicationRepository.findAllByUserIdAndStatusInOrderByIdDesc(userId, statuses);
    }

    @Override
    public List<Application> findAllByUserIdAndStatusInOrFailureReasonIn(
            Long userId,
            Collection<ApplicationStatus> statuses,
            Collection<FailureReason> failureReasons) {
        return springDataJpaApplicationRepository.findAllByUserIdAndStatusInOrFailureReasonIn(
                userId, statuses, failureReasons);
    }

    @Override
    public List<Application> findExpiredPending(LocalDateTime now, int limit) {
        return springDataJpaApplicationRepository.findExpiredPending(now, PageRequest.of(0, limit));
    }

    @Override
    public long countByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses) {
        return springDataJpaApplicationRepository.countByCampaignIdAndStatusIn(campaignId, statuses);
    }

    @Override
    public List<Application> findAllByCampaignIdAndStatusIn(
            Long campaignId, Collection<ApplicationStatus> statuses) {
        return springDataJpaApplicationRepository.findAllByCampaignIdAndStatusIn(campaignId, statuses);
    }

    @Override
    public int cancelWithReason(Long applicationId, Collection<ApplicationStatus> statuses,
                                FailureReason reason) {
        return springDataJpaApplicationRepository.cancelWithReason(
                applicationId, statuses, reason, LocalDateTime.now());
    }

    @Override
    public int confirmIfPending(Long applicationId, String transactionId) {
        return springDataJpaApplicationRepository.confirmIfPending(
                applicationId, transactionId, LocalDateTime.now());
    }

    @Override
    public int failIfPending(Long applicationId, FailureReason reason) {
        return springDataJpaApplicationRepository.failIfPending(
                applicationId, reason, LocalDateTime.now());
    }

    @Override
    public int markUnknownIfPending(Long applicationId) {
        return springDataJpaApplicationRepository.markUnknownIfPending(
                applicationId, LocalDateTime.now());
    }

    @Override
    public int cancelIfConfirmed(Long applicationId) {
        return springDataJpaApplicationRepository.cancelIfConfirmed(
                applicationId, LocalDateTime.now());
    }
}
