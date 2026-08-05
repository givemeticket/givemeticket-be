package kr.givemeticket.api.apply.infrastructure;

import java.util.Optional;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaApplicationRepository implements ApplicationRepository {

    private final SpringDataJpaApplicationRepository springDataJpaApplicationRepository;

    @Override
    public Application save(Application application) {
        return springDataJpaApplicationRepository.save(application);
    }

    @Override
    public Optional<Application> findById(Long applicationId) {
        return springDataJpaApplicationRepository.findById(applicationId);
    }
}
