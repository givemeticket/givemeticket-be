package kr.givemeticket.api.apply.infrastructure;

import kr.givemeticket.api.apply.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaApplicationRepository extends JpaRepository<Application, Long> {
}
