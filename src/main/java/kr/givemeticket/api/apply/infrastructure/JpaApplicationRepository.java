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
    public long countByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses) {
        return springDataJpaApplicationRepository.countByCampaignIdAndStatusIn(campaignId, statuses);
    }

    @Override
    public boolean existsByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses) {
        return springDataJpaApplicationRepository.existsByCampaignIdAndStatusIn(campaignId, statuses);
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
