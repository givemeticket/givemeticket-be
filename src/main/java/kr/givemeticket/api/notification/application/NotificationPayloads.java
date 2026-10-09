package kr.givemeticket.api.notification.application;

import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.notification.domain.NotificationPayload;
import kr.givemeticket.api.user.application.UserService;
import kr.givemeticket.api.user.application.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모든 알림에 공통으로 들어가는 행사 정보(제목, 공유 코드, 주최자 닉네임)를 지금 값으로 채운다.
 * 알림마다 따로 붙는 값(변경 내역, 시각)은 부르는 쪽이 덧붙인다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPayloads {

    private final CampaignRepository campaignRepository;
    private final UserService userService;

    public NotificationPayload of(Long campaignId) {
        return of(campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalStateException("campaign not found: " + campaignId)));
    }

    /** 주최자를 찾지 못해도 알림은 만든다. 닉네임 한 줄이 빠질 뿐이다. */
    public NotificationPayload of(Campaign campaign) {
        String ownerNickname = userService.findUser(campaign.getOwnerId())
                .map(UserResponse::nickname)
                .orElse(null);
        return NotificationPayload.of(campaign.getTitle(), campaign.getShortCode(), ownerNickname);
    }
}
