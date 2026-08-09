package kr.givemeticket.api.apply.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import kr.givemeticket.api.global.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 한 캠페인에 한 사용자당 한 행만 존재한다. 실패·취소 후 재신청하면 새 행을 만들지 않고
 * 이 행을 다시 PENDING으로 되돌린다({@link #reserve}). 이력이 필요해지면 별도 테이블로 뺀다.
 */
@Getter
@Entity
@Table(name = "application", uniqueConstraints = {
        @UniqueConstraint(name = "uk_application_campaign_user", columnNames = {"campaign_id", "user_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseEntity {

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 네이티브 ENUM 으로 잡히면 상수를 추가할 때마다 컬럼 타입을 바꿔야 한다.
    // ddl-auto=update 는 기존 컬럼 타입을 건드리지 않아서 그대로 런타임 오류가 된다.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "failure_reason", length = 32)
    private FailureReason failureReason;

    /** 결제 멱등키. 재시도와 정산 조회가 모두 이 키를 쓴다. */
    @Column(name = "payment_key", length = 64)
    private String paymentKey;

    @Column(name = "transaction_id")
    private String transactionId;

    /** PENDING 홀드 만료 시각. 결제가 필요 없는 신청은 null이다. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 결제 요청을 실제로 내보낸 시각. 크래시 복구 시 "보냈는지 여부"를 구분하는 근거다. */
    @Column(name = "payment_requested_at")
    private LocalDateTime paymentRequestedAt;

    @Column(name = "reconcile_attempts", nullable = false)
    private int reconcileAttempts;

    private Application(Long campaignId, Long userId, ApplicationStatus status) {
        this.campaignId = campaignId;
        this.userId = userId;
        this.status = status;
        this.reconcileAttempts = 0;
    }

    /**
     * 결제가 필요한 캠페인. 재고를 잡은 채 결제 결과를 기다린다.
     */
    public static Application pending(Long campaignId, Long userId, String paymentKey, LocalDateTime expiresAt) {
        Application application = new Application(campaignId, userId, ApplicationStatus.PENDING);
        application.paymentKey = paymentKey;
        application.expiresAt = expiresAt;
        return application;
    }

    /**
     * 결제가 필요 없는 캠페인. 중간 상태 없이 바로 확정된다.
     */
    public static Application confirmed(Long campaignId, Long userId) {
        return new Application(campaignId, userId, ApplicationStatus.CONFIRMED);
    }

    /**
     * 종결된 신청을 다시 PENDING으로 되돌린다. 이전 시도의 결제 흔적은 모두 지운다.
     */
    public void reserve(String paymentKey, LocalDateTime expiresAt) {
        this.status = ApplicationStatus.PENDING;
        this.paymentKey = paymentKey;
        this.expiresAt = expiresAt;
        this.failureReason = null;
        this.transactionId = null;
        this.paymentRequestedAt = null;
        this.reconcileAttempts = 0;
    }

    /**
     * 결제 없이 즉시 확정. 재신청 경로에서 쓴다.
     */
    public void reserveConfirmed() {
        this.status = ApplicationStatus.CONFIRMED;
        this.paymentKey = null;
        this.expiresAt = null;
        this.failureReason = null;
        this.transactionId = null;
        this.paymentRequestedAt = null;
        this.reconcileAttempts = 0;
    }

    public void markPaymentRequested(LocalDateTime now) {
        this.paymentRequestedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isPending() {
        return this.status == ApplicationStatus.PENDING;
    }

    public boolean isActive() {
        return this.status.isActive();
    }

    public boolean isExpired(LocalDateTime now) {
        return this.expiresAt != null && now.isAfter(this.expiresAt);
    }

    /**
     * 아직 결과가 정해지지 않아 사용자가 기다려야 하는 상태.
     */
    public boolean isSettled() {
        return this.status != ApplicationStatus.PENDING
                && this.status != ApplicationStatus.UNKNOWN
                && this.status != ApplicationStatus.MANUAL_REVIEW;
    }
}
