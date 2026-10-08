package kr.givemeticket.api.wish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.givemeticket.api.global.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 행사 하나를 찜했다. 한 사람이 한 행사에 하나만 가진다.
 *
 * <p>찜 수를 캠페인에 카운터 컬럼으로 두지 않고 행을 센다. 카운터를 두면 찜·해제마다 캠페인 행을
 * 갱신해야 해서 인기 행사일수록 같은 행에 잠금이 몰리고, 캠페인 캐시도 매번 무효화된다.
 * 행사 하나의 찜 수는 {@code campaign_id} 인덱스 범위만 세면 된다.
 */
@Getter
@Entity
@Table(name = "wish",
        uniqueConstraints = {
                // 내 찜 목록도 이 인덱스로 읽는다(user_id 가 앞이다).
                @UniqueConstraint(name = "uk_wish_user_campaign", columnNames = {"user_id", "campaign_id"})
        },
        indexes = {
                // 찜 수 세기와, 오픈 알림을 받을 사람 고르기에 쓴다.
                @Index(name = "idx_wish_campaign", columnList = "campaign_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wish extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    public Wish(Long userId, Long campaignId) {
        this.userId = userId;
        this.campaignId = campaignId;
    }
}
