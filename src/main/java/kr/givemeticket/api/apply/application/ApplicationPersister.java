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
            existing.reserveConfirmed(event.occurredAt());
            return;
        }
        applicationRepository.create(Application.confirmed(
                event.applicationId(), event.campaignId(), event.userId(), event.occurredAt()));
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

    /**
     * 주최자가 신청 하나를 취소한다. 확정된 건만 대상이며, 0행이면 그 사이 신청자가
     * 직접 취소한 것이므로 호출자는 재고를 건드리면 안 된다.
     */
    @Transactional
    public int cancelByOwner(Long applicationId) {
        return applicationRepository.cancelWithReason(
                applicationId, ApplicationStatus.active(), FailureReason.CANCELLED_BY_OWNER);
    }
}
