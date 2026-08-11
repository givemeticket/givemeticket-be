package kr.givemeticket.api.user.application;

import java.util.Optional;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.user.domain.User;
import kr.givemeticket.api.user.domain.UserException;
import kr.givemeticket.api.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPersister userPersister;

    /**
     * 첫 로그인이면 가입까지 함께 끝낸다. 닉네임과 프로필 이미지는 제공자가 준 값을 그대로 쓴다.
     *
     * <p>이미 가입한 유저의 닉네임은 덮어쓰지 않는다. 제공자 쪽에서 이름을 바꿨다고 해서
     * 우리 서비스의 표시 이름까지 따라 바뀌어야 할 이유는 없다.
     */
    public Long getOrCreateUserId(ProviderPrincipal principal) {
        Optional<Long> existing = findUserId(principal);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            Long userId = userPersister.save(principal);
            log.info("user registered: userId={}, provider={}", userId, principal.provider());
            return userId;

        } catch (DataIntegrityViolationException e) {
            // 같은 계정으로 첫 로그인이 동시에 들어왔다. 유니크 제약이 걸러주고 먼저 만들어진 쪽을 쓴다.
            return findUserId(principal).orElseThrow(UserException::notFound);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Long> findUserId(ProviderPrincipal principal) {
        return userRepository
                .findByProviderIdAndProvider(principal.providerId(), principal.provider())
                .map(User::getId);
    }
}
