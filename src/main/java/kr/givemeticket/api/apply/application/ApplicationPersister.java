package kr.givemeticket.api.apply.application;

import java.time.LocalDateTime;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.FailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationPersister {

    private final ApplicationRepository applicationRepository;

    @Transactional
    public Application reserve(Long campaignId, Long userId, String paymentKey, LocalDateTime expiresAt) {
        Application application = findReusable(campaignId, userId);
        if (application == null) {
            return applicationRepository.save(
                    Application.pending(campaignId, userId, paymentKey, expiresAt));
        }
        application.reserve(paymentKey, expiresAt);
        return application;
    }

    @Transactional
    public Application confirmImmediately(Long campaignId, Long userId) {
        Application application = findReusable(campaignId, userId);
        if (application == null) {
            return applicationRepository.save(Application.confirmed(campaignId, userId));
        }
        application.reserveConfirmed();
        return application;
    }

    @Transactional
    public void markPaymentRequested(Long applicationId) {
        applicationRepository.findById(applicationId)
                .ifPresent(application -> application.markPaymentRequested(LocalDateTime.now()));
    }

    @Transactional
    public int confirm(Long applicationId, String transactionId) {
        return applicationRepository.confirmIfPending(applicationId, transactionId);
    }

    @Transactional
    public int fail(Long applicationId, FailureReason reason) {
        return applicationRepository.failIfPending(applicationId, reason);
    }

    @Transactional
    public int markUnknown(Long applicationId) {
        return applicationRepository.markUnknownIfPending(applicationId);
    }

    @Transactional
    public int cancel(Long applicationId) {
        return applicationRepository.cancelIfConfirmed(applicationId);
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
