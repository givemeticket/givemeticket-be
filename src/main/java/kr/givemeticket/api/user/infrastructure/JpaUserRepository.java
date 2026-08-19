package kr.givemeticket.api.user.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.user.domain.User;
import kr.givemeticket.api.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserRepository implements UserRepository {

    private final SpringDataJpaUserRepository springDataJpaUserRepository;

    @Override
    public Optional<User> findById(Long userId) {
        return springDataJpaUserRepository.findById(userId);
    }

    @Override
    public List<User> findAllByIdIn(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return springDataJpaUserRepository.findAllById(userIds);
    }

    @Override
    public Optional<User> findByProviderIdAndProvider(String providerId, Provider provider) {
        return springDataJpaUserRepository.findByProviderIdAndProvider(providerId, provider);
    }


    @Override
    public User save(User user) {
        return springDataJpaUserRepository.save(user);
    }
}
