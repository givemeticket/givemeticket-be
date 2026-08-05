package kr.givemeticket.api.apply.application;

import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationPersister {

    private final ApplicationRepository applicationRepository;

    @Transactional
    public Application persist(Long campaignId, Long userId) {
        return applicationRepository.save(new Application(campaignId, userId));
    }
}
