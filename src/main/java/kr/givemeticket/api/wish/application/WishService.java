package kr.givemeticket.api.wish.application;

import kr.givemeticket.api.campaign.application.CampaignApplicationException;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.wish.application.dto.response.WishStatusResponse;
import kr.givemeticket.api.wish.domain.Wish;
import kr.givemeticket.api.wish.domain.WishRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 찜과 찜 해제는 몇 번을 눌러도 결과가 같다. 하트 버튼을 연타하거나 요청이 재전송돼도
 * 오류 없이 "찜한 상태" 또는 "안 한 상태"로 끝난다.
 *
 * <p>트랜잭션으로 감싸지 않는다. 동시에 들어온 같은 찜은 유니크 제약에 걸리는데, 그 예외를
 * 트랜잭션 안에서 잡으면 트랜잭션이 이미 롤백 표시가 된 뒤라 응답을 만들 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishService {

    private final WishRepository wishRepository;
    private final CampaignRepository campaignRepository;

    /** 삭제된 행사는 찜할 수 없다. 종료된 행사는 막지 않는다 — 찜해도 해가 없다. */
    public WishStatusResponse wish(Long userId, Long campaignId) {
        findLiveCampaign(campaignId);

        if (!wishRepository.exists(userId, campaignId)) {
            try {
                wishRepository.save(new Wish(userId, campaignId));
            } catch (DataIntegrityViolationException e) {
                // 같은 사용자의 찜이 한발 먼저 들어갔다. 원하는 결과와 같다.
                log.debug("wish already exists: userId={}, campaignId={}", userId, campaignId);
            }
        }
        return new WishStatusResponse(campaignId, wishRepository.countByCampaignId(campaignId), true);
    }

    /**
     * 행사 상태를 보지 않는다. 삭제된 행사도 찜 목록에 남아 있어서, 거기서 하트를 끌 수 있어야 한다.
     */
    public WishStatusResponse unwish(Long userId, Long campaignId) {
        wishRepository.delete(userId, campaignId);
        return new WishStatusResponse(campaignId, wishRepository.countByCampaignId(campaignId), false);
    }

    /**
     * @param userId 로그인하지 않았으면 null. 이때 wished 도 null 이다
     */
    public WishStatusResponse getWishStatus(Long campaignId, Long userId) {
        findLiveCampaign(campaignId);

        Boolean wished = (userId == null) ? null : wishRepository.exists(userId, campaignId);
        return new WishStatusResponse(campaignId, wishRepository.countByCampaignId(campaignId), wished);
    }

    /** 탈퇴 정리. 찜 수에서도 빠진다. */
    public void deleteAllOf(Long userId) {
        int deleted = wishRepository.deleteAllByUserId(userId);
        if (deleted > 0) {
            log.info("wishes deleted by user withdrawal: userId={}, count={}", userId, deleted);
        }
    }

    private Campaign findLiveCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(CampaignApplicationException::campaignNotFound);
        if (campaign.isDeleted()) {
            throw CampaignApplicationException.campaignDeleted();
        }
        return campaign;
    }
}
