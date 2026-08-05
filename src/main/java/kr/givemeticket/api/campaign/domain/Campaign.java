package kr.givemeticket.api.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import kr.givemeticket.api.global.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "campaign")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CampaignType type;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CampaignStatus status;

    public Campaign(String title, CampaignType type, int totalStock, LocalDateTime openAt) {
        this.title = title;
        this.type = type;
        this.totalStock = totalStock;
        this.openAt = openAt;
        this.status = CampaignStatus.SCHEDULED;
    }

    public void open() {
        this.status = CampaignStatus.OPEN;
    }

    public void close() {
        this.status = CampaignStatus.CLOSED;
    }

    public boolean isOpen(LocalDateTime now) {
        return status == CampaignStatus.OPEN && !now.isBefore(openAt);
    }
}
