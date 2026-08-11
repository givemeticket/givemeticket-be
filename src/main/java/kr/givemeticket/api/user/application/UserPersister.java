package kr.givemeticket.api.user.application;

import java.time.LocalDateTime;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.user.domain.User;
import kr.givemeticket.api.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 생성만 짧은 트랜잭션으로 끊어준다.
 *
 * <p>같은 계정으로 첫 로그인이 동시에 들어오면 유니크 제약에 걸리는데, 그 트랜잭션은 롤백된 상태라
 * 같은 트랜잭션 안에서 다시 조회할 수 없다. 저장을 따로 떼어 두면 실패한 트랜잭션이 여기서 끝나고,
 * 호출자는 새 트랜잭션으로 이미 만들어진 유저를 읽을 수 있다.
 */
@Service
@RequiredArgsConstructor
public class UserPersister {

    private final UserRepository userRepository;

    /**
     * 개인정보를 지우는 것만 짧은 트랜잭션으로 끊는다.
     * 앞선 취소·환불이 외부 호출이라 같은 트랜잭션에 묶을 수 없다.
     */
    @Transactional
    public void withdraw(Long userId) {
        userRepository.findById(userId)
                .ifPresent(user -> user.withdraw(LocalDateTime.now()));
    }

    @Transactional
    public Long save(ProviderPrincipal principal) {
        User user = new User(
                principal.nickname(),
                principal.profileImageUrl(),
                principal.providerId(),
                principal.provider());

        return userRepository.save(user).getId();
    }
}
