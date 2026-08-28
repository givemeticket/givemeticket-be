package kr.givemeticket.api.apply.application;

import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.ReservationEvent;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationPersister {

    private final ApplicationRepository applicationRepository;

    /**
     * 큐에서 꺼낸 예매를 저장한다. 워커가 부르는 유일한 쓰기 경로다.
     *
     * <p><b>몇 번을 불러도 결과가 같다.</b> 번호가 이미 정해져 있어, 그 id 의 행이
     * 있으면 확정 상태로 맞추고 없으면 만든다. 재전달이든 취소된 행 되쓰기든 같은 일이다.
     */
    @Transactional
    public void persist(ReservationEvent event) {
        Application existing = applicationRepository.findById(event.applicationId()).orElse(null);
        if (existing != null) {
            existing.reserveConfirmed();
            return;
        }
        applicationRepository.create(Application.confirmed(
                event.applicationId(), event.campaignId(), event.userId()));
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
}
