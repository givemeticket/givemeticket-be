package kr.givemeticket.api.apply.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import kr.givemeticket.api.global.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "application", indexes = {
        @Index(name = "idx_application_campaign_user", columnList = "campaign_id, user_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseEntity {

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApplicationStatus status;

    @Column(name = "transaction_id")
    private String transactionId;

    public Application(Long campaignId, Long userId) {
        this.campaignId = campaignId;
        this.userId = userId;
        this.status = ApplicationStatus.PENDING;
    }

    public void confirm(String transactionId) {
        this.status = ApplicationStatus.CONFIRMED;
        this.transactionId = transactionId;
    }

    public void fail() {
        this.status = ApplicationStatus.FAILED;
    }

    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isPending() {
        return this.status == ApplicationStatus.PENDING;
    }
}
