package kr.givemeticket.api.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

    @Column(nullable = false)
    private String nickname;

    /**
     * 프로필 이미지가 없는 계정도 있으므로 선택이다.
     */
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    /**
     * 제공자가 주는 회원 식별자. 카카오는 숫자지만 네이버는 영숫자 문자열이라 문자열로 받는다.
     */
    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    public User(String nickname, String profileImageUrl, String providerId, Provider provider) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.providerId = providerId;
        this.provider = provider;
    }
}
