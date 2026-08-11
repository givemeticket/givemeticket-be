package kr.givemeticket.api.user.infrastructure;

import java.util.Optional;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderIdAndProvider(String providerId, Provider provider);

}
