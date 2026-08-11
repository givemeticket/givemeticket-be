package kr.givemeticket.api.user.domain;

import java.util.Optional;
import kr.givemeticket.api.login.domain.Provider;

public interface UserRepository {

    Optional<User> findById(Long userId);

    Optional<User> findByProviderIdAndProvider(String providerId, Provider provider);

    User save(User user);
}
