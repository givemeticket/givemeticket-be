package kr.givemeticket.api.user.application;

import java.util.List;
import kr.givemeticket.api.apply.application.ApplicationService;
import kr.givemeticket.api.campaign.application.CampaignService;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.login.application.ProviderUnlinkService;
import kr.givemeticket.api.user.domain.User;
import kr.givemeticket.api.user.domain.UserException;
import kr.givemeticket.api.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴. 남아 있는 것을 먼저 정리하고 마지막에 개인정보를 지운다.
 *
 * <p>정리 과정에서 환불이 여러 번 나가므로 트랜잭션으로 감싸지 않는다.
 * 상태 전이는 각 Persister 가 건별로 짧게 끊는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawService {

    private final UserRepository userRepository;
    private final UserPersister userPersister;
    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;
    private final ApplicationService applicationService;
    private final ProviderUnlinkService providerUnlinkService;

    /**
     * 순서가 중요하다. 내가 연 캠페인을 먼저 지워야 그 캠페인에 건 내 신청도 함께 정리되고,
     * 남은 내 신청만 뒤에서 처리하면 된다.
     *
     * <p>소셜 연결 끊기는 그보다도 앞에 둔다. 여기서 실패하면 아직 아무것도 지우지 않은 상태라
     * 같은 요청을 그대로 재시도할 수 있다. 반대로 뒤에 두면 캠페인 삭제·환불이 이미 나간 뒤에
     * 실패할 수 있고, 되돌릴 방법이 없다. 회원번호도 지우기 전에 읽어야 남아 있다.
     */
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserException::notFound);
        if (user.isWithdrawn()) {
            throw UserException.notFound();
        }

        providerUnlinkService.unlink(user.getProvider(), user.getProviderId());

        int deletedCampaigns = deleteOwnedCampaigns(userId);
        int cancelledApplications = applicationService.cancelAllByUserWithdrawal(userId);

        userPersister.withdraw(userId);

        log.info("user withdrawn: userId={}, deletedCampaigns={}, cancelledApplications={}",
                userId, deletedCampaigns, cancelledApplications);
    }

    /**
     * 내가 연 행사는 참가자 전원 취소·환불까지 포함해 지운다. 캠페인 삭제와 같은 경로를 탄다.
     */
    private int deleteOwnedCampaigns(Long userId) {
        List<Campaign> owned = campaignRepository.findAllOwnedBy(userId);

        for (Campaign campaign : owned) {
            campaignService.deleteCampaign(campaign.getId(), userId);
        }
        return owned.size();
    }
}
