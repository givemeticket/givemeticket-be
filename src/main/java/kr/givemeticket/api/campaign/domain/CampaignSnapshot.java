package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;

public record CampaignSnapshot(
        Long id,
        Long ownerId,
        String shortCode,
        String title,
        CampaignType type,
        int totalStock,
        LocalDateTime openAt,
        CampaignStatus status,
        Detail detail
) {
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
                campaign.getStatus(),
                Detail.from(campaign.getDetail()));
    }

    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }

    public boolean isCacheable(LocalDateTime now) {
        if (status == CampaignStatus.DELETED || status == CampaignStatus.CLOSED) {
            return false;
        }
        return detail == null
                || detail.eventEndAt() == null
                || !detail.eventEndAt().isBefore(now);
    }
}
