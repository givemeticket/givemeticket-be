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
 * 한 사람에게 간 알림 한 건. 사람마다 저장소를 따로 두지 않는다 — {@code (user_id, id)} 인덱스가
 * 곧 그 사람의 알림함이다.
 *
 * <p>받는 사람 수만큼 행이 생긴다(쓸 때 펼치기). 행사 하나의 수신자는 정원으로 상한이 잡혀 있어
 * 행이 무한히 늘지 않고, 대신 읽기는 {@code WHERE user_id = ?} 한 번으로 끝난다.
 */
@Getter
@Entity
@Table(name = "notification",
        uniqueConstraints = {
                // 팬아웃이 같은 원본을 두 번 펼쳐도 행이 늘지 않게 한다. 키가 비어 있으면(NULL) 걸리지 않는다.
                @UniqueConstraint(name = "uk_notification_user_dedupe",
                        columnNames = {"user_id", "dedupe_key"})
        },
        indexes = {
                @Index(name = "idx_notification_user_id", columnList = "user_id, id"),
                @Index(name = "idx_notification_user_read", columnList = "user_id, read_at"),
                // 보관 기간이 지난 알림을 지울 때 쓴다.
                @Index(name = "idx_notification_created_at", columnList = "created_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    public static final int DEDUPE_KEY_MAX_LENGTH = 100;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private NotificationPayload payload;

    /** 같은 원본에서 나온 알림을 가려낸다. 팬아웃으로 만든 알림에만 있다. */
    @Column(name = "dedupe_key", length = DEDUPE_KEY_MAX_LENGTH)
    private String dedupeKey;

    /** 사용자가 확인한 시각. 비어 있으면 안 읽은 알림이다. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    public Notification(
            Long userId,
            Long campaignId,
            NotificationType type,
            NotificationPayload payload,
            String dedupeKey
    ) {
        this.userId = userId;
        this.campaignId = campaignId;
        this.type = type;
        this.payload = payload;
        this.dedupeKey = dedupeKey;
    }

    /** 이미 읽었으면 처음 읽은 시각을 그대로 둔다. */
    public void markRead(LocalDateTime now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isRead() {
        return readAt != null;
    }
}
