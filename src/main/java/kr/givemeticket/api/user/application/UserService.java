package kr.givemeticket.api.user.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.user.application.dto.response.UserResponse;
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

    /**
     * 탈퇴한 계정도 404 로 감추지 않는다. 신청 이력에 남은 userId 를 화면에 그릴 때
     * "탈퇴한 사용자"라고 보여줄 수 있어야 한다.
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(UserException::notFound);
    }

    /**
     * 목록 화면에서 작성자 이름을 채우기 위한 일괄 조회. 없는 userId 는 키 자체가 빠진다.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> findNicknames(Collection<Long> userIds) {
        return userRepository.findAllByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    @Transactional(readOnly = true)
    public Optional<Long> findUserId(ProviderPrincipal principal) {
        return userRepository
                .findByProviderIdAndProvider(principal.providerId(), principal.provider())
                .map(User::getId);
    }
}
