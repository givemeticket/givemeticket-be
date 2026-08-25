package kr.givemeticket.api.campaign.application;

import kr.givemeticket.api.campaign.domain.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 상태 전이를 짧은 트랜잭션으로 끊어준다.
 *
 * <p>삭제는 뒤이어 남은 신청을 건별로 취소하는 작업이 따라붙는데,
 * 이를 한 트랜잭션에 묶으면 신청이 많은 행사일수록 커넥션을 오래 잡고 있게 된다.
 */
@Service
@RequiredArgsConstructor
public class CampaignPersister {

    private final CampaignRepository campaignRepository;

    /**
     * @return 실제로 바뀐 행 수. 0이면 그 사이 다른 요청이 이미 삭제한 것이므로
     *         호출자는 신청 취소를 중복해서 돌리면 안 된다
     */
    @Transactional
    public int markDeleted(Long campaignId) {
        return campaignRepository.markDeleted(campaignId);
    }
}
