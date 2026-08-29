package kr.givemeticket.api.apply.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
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
        },
        indexes = {
                // 주최자의 신청자 목록이 캠페인 하나를 신청 순서대로 훑는다.
                @Index(name = "idx_application_campaign_applied_at",
                        columnList = "campaign_id, applied_at")
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

    /**
     * 자리를 잡은 시각. {@code createdAt} 과 다르다 — 저장이 비동기라 행이 만들어지는 시각은
     * 워커가 큐에서 꺼낸 때이고, 재시도가 끼면 한참 뒤일 수도 있다. 선착순 순서를 말하려면
     * 요청이 Redis 에서 자리를 잡은 시각이어야 한다.
     *
     * <p>취소했다가 다시 신청하면 같은 행을 되쓰므로 이 값도 함께 갱신된다.
     * 그래야 "다시 신청한 시각"이 남는다.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    private Application(
            Long id, Long campaignId, Long userId, ApplicationStatus status, LocalDateTime appliedAt) {
        this.id = id;
        this.campaignId = campaignId;
        this.userId = userId;
        this.status = status;
        this.appliedAt = appliedAt;
    }

    public static Application confirmed(
            Long id, Long campaignId, Long userId, LocalDateTime appliedAt) {
        return new Application(id, campaignId, userId, ApplicationStatus.CONFIRMED, appliedAt);
    }

    /**
     * 종결된 신청을 되살려 다시 자리를 잡는다. 새 행을 만들지 않는 건
     * {@code (campaign_id, user_id)} 유니크 제약 때문이다.
     */
    public void reserveConfirmed(LocalDateTime appliedAt) {
        this.status = ApplicationStatus.CONFIRMED;
        this.failureReason = null;
        this.appliedAt = appliedAt;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isActive() {
        return this.status.isActive();
    }

    /**
     * 신청 시각. 컬럼이 생기기 전에 저장된 행은 비어 있으므로 생성 시각으로 대신한다.
     * 백필(docs/sql/2026-08-29-application-applied-at.sql)이 끝나면 이 갈래는 타지 않는다.
     */
    public LocalDateTime appliedAt() {
        return (appliedAt != null) ? appliedAt : getCreatedAt();
    }
}
