package kr.givemeticket.api.apply.domain;

import java.util.Optional;

public interface ApplicationRepository {

    Application save(Application application);

    Optional<Application> findById(Long applicationId);
}
