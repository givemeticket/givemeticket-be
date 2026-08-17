package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;

/**
 * 캐시에 담는 캠페인의 값 스냅샷.
 *
 * <p>엔티티를 그대로 직렬화하지 않는다. 캐시에서 꺼낸 객체가 영속 상태로 오해받으면 안 되고,
 * 캐시에 담긴 포맷이 JPA 매핑 변경에 끌려다니면 안 되기 때문이다.
 *
 * <p><b>재고는 담지 않는다.</b> 재고는 Redis 카운터가 진실이고 요청마다 달라진다.
 * 여기에 넣는 순간 캐시가 매진을 숨기게 된다.
 */
public record CampaignSnapshot(
        Long id,
        Long ownerId,
        String shortCode,
        String title,
        CampaignType type,
        int totalStock,
        LocalDateTime openAt,
        boolean requiresPayment,
        CampaignStatus status,
        Detail detail
) {

    /**
     * {@link CampaignDetail} 의 값 사본. 도메인이 응답 DTO 를 참조하지 않도록 따로 둔다.
     */
    public record Detail(
            String content,
            LocalDateTime eventAt,
            LocalDateTime eventEndAt,
            String location,
            String address,
            String imageUrl,
            String contact,
            Integer price
    ) {

        static Detail from(CampaignDetail detail) {
            if (detail == null) {
                return null;
            }
            return new Detail(
                    detail.getContent(),
                    detail.getEventAt(),
                    detail.getEventEndAt(),
                    detail.getLocation(),
                    detail.getAddress(),
                    detail.getImageUrl(),
                    detail.getContact(),
                    detail.getPrice());
        }
    }

    public static CampaignSnapshot from(Campaign campaign) {
        return new CampaignSnapshot(
                campaign.getId(),
                campaign.getOwnerId(),
                campaign.getShortCode(),
                campaign.getTitle(),
                campaign.getType(),
                campaign.getTotalStock(),
                campaign.getOpenAt(),
                campaign.isRequiresPayment(),
                campaign.getStatus(),
                Detail.from(campaign.getDetail()));
    }

    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }

    public boolean isDeleted() {
        return status == CampaignStatus.DELETED;
    }

    /**
     * 캐시에 올릴 값인지. 끝난 행사는 다시 몰려서 조회될 일이 없는데 캐시(=메모리)만 차지한다.
     *
     * <p>판단 기준이 상태 하나가 아닌 이유는, 현재 {@link Campaign#close()} 를 호출하는 곳이
     * 없어서 {@link CampaignStatus#CLOSED} 로 가는 경로가 실제로는 없기 때문이다.
     * 행사 종료 시각이 지났으면 상태와 무관하게 끝난 것으로 본다.
     */
    public boolean isCacheable(LocalDateTime now) {
        if (status == CampaignStatus.DELETED || status == CampaignStatus.CLOSED) {
            return false;
        }
        return detail == null
                || detail.eventEndAt() == null
                || !detail.eventEndAt().isBefore(now);
    }
}
