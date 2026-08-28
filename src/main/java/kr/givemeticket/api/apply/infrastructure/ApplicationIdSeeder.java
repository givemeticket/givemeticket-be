package kr.givemeticket.api.apply.infrastructure;

import kr.givemeticket.api.apply.domain.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 채번 카운터를 이미 저장된 예매의 최대 id 이상으로 맞춘다. DB 가 id 를 발급하던 시절의
 * 행들과 PK 가 부딪히지 않게 하려는 것이다.
 *
 * <p>카운터를 잃어버린 뒤에도 같은 방식으로 복구되지만, <b>발급됐지만 아직 저장되지 않은
 * 번호는 되살아나지 못한다</b>. 그 창을 막는 것은 Redis 영속화(AOF)의 몫이다.
 *
 * <p>언제: 애플리케이션 기동 직후 한 번.
 */
@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class ApplicationIdSeeder implements ApplicationRunner {

    private final RedisApplicationIdIssuer applicationIdIssuer;
    private final ApplicationRepository applicationRepository;

    /** 카운터를 한 번 맞춘다. */
    @Override
    public void run(ApplicationArguments args) {
        long maxId = applicationRepository.findMaxId();
        long counter = applicationIdIssuer.seedAtLeast(maxId);
        log.info("application id counter ready: maxStoredId={}, counter={}", maxId, counter);
    }
}
