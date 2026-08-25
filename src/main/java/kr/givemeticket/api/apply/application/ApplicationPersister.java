package kr.givemeticket.api.apply.application;

import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationPersister {

    private final ApplicationRepository applicationRepository;

    @Transactional
    public Application confirm(Long campaignId, Long userId) {
        Application application = findReusable(campaignId, userId);
        if (application == null) {
            return applicationRepository.save(Application.confirmed(campaignId, userId));
        }
        application.reserveConfirmed();
        return application;
    }

    @Transactional
    public int cancel(Long applicationId) {
        return applicationRepository.cancelIfConfirmed(applicationId);
    }

    @Transactional
    public int cancelByCampaignDeletion(Long applicationId) {
        return applicationRepository.cancelWithReason(
                applicationId, ApplicationStatus.active(), FailureReason.CAMPAIGN_DELETED);
    }

    @Transactional
    public int cancelByUserWithdrawal(Long applicationId) {
        return applicationRepository.cancelWithReason(
                applicationId, ApplicationStatus.active(), FailureReason.USER_WITHDRAWN);
    }

    private Application findReusable(Long campaignId, Long userId) {
        Application application = applicationRepository
                .findByCampaignIdAndUserId(campaignId, userId)
                .orElse(null);
        if (application != null && application.isActive()) {
            throw ApplyApplicationException.alreadyApplied();
        }
        return application;
    }
}
