package kr.givemeticket.api.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import kr.givemeticket.api.global.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "campaign", indexes = {
        @Index(name = "uk_campaign_short_code", columnList = "short_code", unique = true),
        @Index(name = "idx_campaign_owner", columnList = "owner_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "short_code", nullable = false, length = 16)
    private String shortCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 32)
    private CampaignType type;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Column(name = "requires_payment", nullable = false)
    private boolean requiresPayment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private CampaignStatus status;

    /** 행사 안내 정보. 없을 수 있다 — 모든 컬럼이 null이면 Hibernate가 null로 준다. */
    @Embedded
    private CampaignDetail detail;

    public Campaign(
            Long ownerId,
            String shortCode,
            String title,
            CampaignType type,
            int totalStock,
            LocalDateTime openAt,
            boolean requiresPayment,
            CampaignDetail detail
    ) {
        this.ownerId = ownerId;
        this.shortCode = shortCode;
        this.title = title;
        this.type = type;
        this.totalStock = totalStock;
        this.openAt = openAt;
        this.requiresPayment = requiresPayment;
        this.detail = detail;
        this.status = CampaignStatus.SCHEDULED;
    }

    public void open() {
        this.status = CampaignStatus.OPEN;
    }

    public void close() {
        this.status = CampaignStatus.CLOSED;
    }

    public void delete() {
        this.status = CampaignStatus.DELETED;
    }

    public void changeOpenAt(LocalDateTime openAt) {
        this.openAt = openAt;
    }

    /**
     * 통째로 교체한다. 일부만 바꾸려면 전체를 다시 보내야 한다 —
     * 필드별 병합으로 하면 "값을 지운다"를 표현할 방법이 없다.
     */
    public void changeDetail(CampaignDetail detail) {
        this.detail = (detail == null || detail.isEmpty()) ? null : detail;
    }

    public int changeTotalStock(int newTotalStock) {
        int delta = newTotalStock - this.totalStock;
        this.totalStock = newTotalStock;
        return delta;
    }

    public boolean isOwnedBy(Long userId) {
        return this.ownerId.equals(userId);
    }

    public boolean isScheduled() {
        return status == CampaignStatus.SCHEDULED;
    }

    public boolean isDeleted() {
        return status == CampaignStatus.DELETED;
    }

    public boolean isOpen(LocalDateTime now) {
        return status == CampaignStatus.OPEN && !now.isBefore(openAt);
    }
}
