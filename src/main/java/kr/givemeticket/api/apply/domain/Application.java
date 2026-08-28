package kr.givemeticket.api.apply.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.givemeticket.api.global.domain.BaseTimeEntity;
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
public class Application extends BaseTimeEntity {

    /**
     * DB 가 발급하지 않는다. 좌석 선점 시점에 Redis 에서 채번한 값을 받는다.
     * 응답을 먼저 내보낼 수 있고, 저장이 재시도돼도 같은 PK 라 행이 늘지 않는다.
     */
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

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

    private Application(Long id, Long campaignId, Long userId, ApplicationStatus status) {
        this.id = id;
        this.campaignId = campaignId;
        this.userId = userId;
        this.status = status;
    }

    public static Application confirmed(Long id, Long campaignId, Long userId) {
        return new Application(id, campaignId, userId, ApplicationStatus.CONFIRMED);
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
