package kr.givemeticket.api.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import kr.givemeticket.api.global.domain.BaseEntity;
import kr.givemeticket.api.login.domain.Provider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider",
                // 같은 소셜 계정으로 두 번 가입되는 것을 DB 에서 막는다. 동시 가입 요청의 최종 방어선이다.
                columnNames = {"provider", "provider_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    @Column(nullable = false)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    /**
     * 제공자가 주는 회원 식별자. 카카오는 숫자지만 네이버는 영숫자 문자열이라 문자열로 받는다.
     * 탈퇴하면 비운다 — 그래야 유니크 자리가 풀려 같은 소셜 계정으로 다시 가입할 수 있다.
     */
    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public User(String nickname, String profileImageUrl, String providerId, Provider provider) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.providerId = providerId;
        this.provider = provider;
    }

    /**
     * 행은 남기고 개인정보만 지운다. 신청·정산 이력이 userId 로 이 행을 가리키고 있어
     * 통째로 지우면 환불 추적이 끊긴다.
     *
     * <p>MySQL 은 유니크 제약에서 NULL 을 서로 다른 값으로 보므로, providerId 를 비우면
     * 탈퇴 회원이 여럿이어도 충돌하지 않고 재가입 자리도 열린다.
     */
    public void withdraw(LocalDateTime now) {
        this.nickname = WITHDRAWN_NICKNAME;
        this.profileImageUrl = null;
        this.providerId = null;
        this.deletedAt = now;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }
}
