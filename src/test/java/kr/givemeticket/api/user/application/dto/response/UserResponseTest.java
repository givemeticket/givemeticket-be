package kr.givemeticket.api.user.application.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserResponseTest {

    /**
     * 스킴을 올리기 전에 가입한 회원은 http 로 저장돼 있다. 백필하지 않고 내보낼 때 올린다.
     */
    @Test
    @DisplayName("http 로 저장된 프로필 이미지는 https 로 내려간다")
    void upgradesStoredHttpProfileImage() {
        User user = new User(
                "민기", "http://img1.kakaocdn.net/thumb/default.jpg", "123456", Provider.KAKAO);

        assertThat(UserResponse.from(user).profileImageUrl())
                .isEqualTo("https://img1.kakaocdn.net/thumb/default.jpg");
    }

    @Test
    @DisplayName("탈퇴한 계정은 프로필 이미지가 비어 있다")
    void keepsWithdrawnUserWithoutImage() {
        User user = new User(
                "민기", "http://img1.kakaocdn.net/thumb/default.jpg", "123456", Provider.KAKAO);
        user.withdraw(LocalDateTime.now());

        UserResponse response = UserResponse.from(user);

        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.withdrawn()).isTrue();
    }
}
