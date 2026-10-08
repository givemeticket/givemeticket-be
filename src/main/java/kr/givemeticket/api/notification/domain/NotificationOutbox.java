package kr.givemeticket.api.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
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
 * 아직 신청자에게 펼치지 않은 알림의 원본 한 건.
 *
 * <p>행사 수정·삭제와 <b>같은 트랜잭션</b>에서 한 행만 들어간다. 신청자 N명에게 행을 만드는 일은
 * 워커가 뒤에서 한다. 주최자의 요청이 신청자 수에 비례해 느려지지 않고, 커밋 직후 서버가 죽어도
 * 원본이 남아 있어 알림이 사라지지 않는다.
 */
@Getter
@Entity
@Table(name = "notification_outbox",
        uniqueConstraints = {
                // 임박 알림처럼 스케줄러가 여러 파드에서 같은 원본을 만들려 할 때 하나만 남긴다.
                @UniqueConstraint(name = "uk_notification_outbox_dedupe", columnNames = "dedupe_key")
        },
        indexes = {
                @Index(name = "idx_notification_outbox_processed", columnList = "processed_at, id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox extends BaseEntity {

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private NotificationPayload payload;

    /** 같은 일로 원본이 두 번 생기면 안 되는 경우에만 채운다. 수정·삭제는 매번 새 일이라 비운다. */
    @Column(name = "dedupe_key", length = Notification.DEDUPE_KEY_MAX_LENGTH)
    private String dedupeKey;

    /** 신청자에게 펼친 시각. 비어 있으면 워커가 집어 갈 대상이다. */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public NotificationOutbox(
            Long campaignId, NotificationType type, NotificationPayload payload, String dedupeKey) {
        this.campaignId = campaignId;
        this.type = type;
        this.payload = payload;
        this.dedupeKey = dedupeKey;
    }

    public void markProcessed(LocalDateTime now) {
        this.processedAt = now;
    }

    /**
     * 이 원본에서 펼친 알림이 공유하는 키. 워커가 같은 원본을 다시 집어도
     * {@code (user_id, dedupe_key)} 유니크 제약에 걸려 행이 늘지 않는다.
     */
    public String fanOutKey() {
        return "outbox:" + getId();
    }
}
