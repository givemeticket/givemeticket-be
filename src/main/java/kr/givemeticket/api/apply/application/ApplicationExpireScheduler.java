package kr.givemeticket.api.apply.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제하지 않고 홀드 시간을 넘긴 신청을 회수한다.
 *
 * <p>사용자가 취소한 것과 구분해야 하므로 CANCELLED가 아니라 FAILED(EXPIRED)로 남긴다.
 * 화면에는 둘 다 "취소됨"으로 보여도 되지만, 통계와 장애 추적에서는 달라야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationExpireScheduler {

    private final ApplicationRepository applicationRepository;
    private final ApplicationPersister applicationPersister;
    private final CampaignRepository campaignRepository;
    private final StockRepository stockRepository;

    @Value("${application.expire-batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${application.expire-scheduler-delay-ms:10000}")
    public void expirePendingApplications() {
        List<Application> expired = applicationRepository.findExpiredPending(
                LocalDateTime.now(), batchSize);
        if (expired.isEmpty()) {
            return;
        }

        Map<Long, Campaign> campaigns = campaignRepository
                .findAllByIdIn(expired.stream().map(Application::getCampaignId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Campaign::getId, Function.identity()));

        int released = 0;
        int handedToSettlement = 0;
        for (Application application : expired) {
            Campaign campaign = campaigns.get(application.getCampaignId());
            if (campaign == null) {
                continue;
            }

            if (application.getPaymentRequestedAt() != null) {
                // 결제 요청은 나갔는데 결과가 기록되지 않았다. confirm 도중 서버가 죽은 경우다.
                // 승인됐을 수도 있으므로 만료로 덮지 않고 재고를 잡은 채 정산으로 넘긴다.
                if (applicationPersister.markUnknown(application.getId()) == 1) {
                    handedToSettlement++;
                    log.error("expired with payment in flight, needs reconciliation: "
                                    + "applicationId={}, paymentKey={}",
                            application.getId(), application.getPaymentKey());
                }
                continue;
            }

            // 그 사이 사용자가 confirm을 끝냈으면 0행이 바뀌고, 재고도 건드리지 않는다.
            if (applicationPersister.fail(application.getId(), FailureReason.EXPIRED) == 1) {
                stockRepository.restore(application.getCampaignId(), campaign.getTotalStock());
                released++;
            }
        }

        if (released > 0 || handedToSettlement > 0) {
            log.info("expired pending applications: released={}, unknown={}",
                    released, handedToSettlement);
        }
    }
}
