package kr.givemeticket.api.campaign.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.user.domain.User;
import kr.givemeticket.api.user.domain.UserRepository;

class FakeUserRepository implements UserRepository {

    private final Map<Long, User> users = new LinkedHashMap<>();

    void put(Long userId, User user) {
        users.put(userId, user);
    }

    @Override
    public Optional<User> findById(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public List<User> findAllByIdIn(Collection<Long> userIds) {
        List<User> found = new ArrayList<>();
        for (Long userId : userIds) {
            User user = users.get(userId);
            if (user != null) {
                found.add(user);
            }
        }
        return found;
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
