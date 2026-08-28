package kr.givemeticket.api.apply.infrastructure;

import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    /** id 가 이미 정해져 있으므로 persist 로 곧바로 넣는다. merge 를 피한다. */
    @Override
    public Application create(Application application) {
        entityManager.persist(application);
        return application;
    }

    @Override
    public long findMaxId() {
        return springDataJpaApplicationRepository.findMaxId();
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
    public long countByCampaignIdAndStatusIn(Long campaignId, Collection<ApplicationStatus> statuses) {
        return springDataJpaApplicationRepository.countByCampaignIdAndStatusIn(campaignId, statuses);
    }

    @Override
    public List<Application> findAllByCampaignIdAndStatusIn(
            Long campaignId, Collection<ApplicationStatus> statuses) {
        return springDataJpaApplicationRepository.findAllByCampaignIdAndStatusIn(campaignId, statuses);
    }

    @Override
    public int cancelIfConfirmed(Long applicationId) {
        return springDataJpaApplicationRepository.cancelIfConfirmed(
                applicationId, LocalDateTime.now());
    }

    @Override
    public int cancelWithReason(Long applicationId, Collection<ApplicationStatus> statuses,
                                FailureReason reason) {
        return springDataJpaApplicationRepository.cancelWithReason(
                applicationId, statuses, reason, LocalDateTime.now());
    }
}
