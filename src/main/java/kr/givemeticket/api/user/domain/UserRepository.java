package kr.givemeticket.api.user.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.login.domain.Provider;

public interface UserRepository {

    Optional<User> findById(Long userId);

    List<User> findAllByIdIn(Collection<Long> userIds);

    Optional<User> findByProviderIdAndProvider(String providerId, Provider provider);

    User save(User user);
}
