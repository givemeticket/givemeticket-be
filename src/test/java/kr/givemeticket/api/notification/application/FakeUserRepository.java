package kr.givemeticket.api.notification.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.user.domain.User;
import kr.givemeticket.api.user.domain.UserRepository;

/**
 * 알림에 주최자 닉네임을 채울 때 쓰는 조회만 채운다.
 */
class FakeUserRepository implements UserRepository {

    private final Map<Long, User> users = new LinkedHashMap<>();

    void put(Long userId, String nickname) {
        User user = new User(nickname, null, "provider-" + userId, Provider.KAKAO);
        users.put(userId, TestEntities.with(user, "id", userId));
    }

    @Override
    public Optional<User> findById(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public List<User> findAllByIdIn(Collection<Long> userIds) {
        return userIds.stream().map(users::get).filter(user -> user != null).toList();
    }

    @Override
    public Optional<User> findByProviderIdAndProvider(String providerId, Provider provider) {
        throw new UnsupportedOperationException();
    }

    @Override
    public User save(User user) {
        throw new UnsupportedOperationException();
    }
}
