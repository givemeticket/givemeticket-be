package kr.givemeticket.api.apply.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.givemeticket.api.global.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "application",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_application_campaign_user",
                        columnNames = {"campaign_id", "user_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseEntity {

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "failure_reason", length = 32)
    private FailureReason failureReason;

    private Application(Long campaignId, Long userId, ApplicationStatus status) {
        this.campaignId = campaignId;
        this.userId = userId;
        this.status = status;
    }

    public static Application confirmed(Long campaignId, Long userId) {
        return new Application(campaignId, userId, ApplicationStatus.CONFIRMED);
    }

    /**
     * 종결된 신청을 되살려 다시 자리를 잡는다. 새 행을 만들지 않는 건
     * {@code (campaign_id, user_id)} 유니크 제약 때문이다.
     */
    public void reserveConfirmed() {
        this.status = ApplicationStatus.CONFIRMED;
        this.failureReason = null;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isActive() {
        return this.status.isActive();
    }
}
