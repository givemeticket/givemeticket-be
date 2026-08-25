package kr.givemeticket.api.login.application;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderUnlinkClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 제공자별 연결 끊기 구현을 한곳에 모아 두고, 호출자가 제공자를 몰라도 되게 한다.
 *
 * <p>구현이 없는 제공자는 그냥 건너뛴다. 네이버가 그렇다 — 네이버의 연결 끊기는
 * 사용자의 액세스 토큰을 요구하는데, 우리는 로그인 때 신원만 확인하고 토큰을 버린다.
 * 탈퇴 시점에는 이미 없는 값이라 부를 방법이 없다.
 */
@Slf4j
@Service
public class ProviderUnlinkService {

    private final Map<Provider, ProviderUnlinkClient> unlinkClients;

    /**
     * 구현체가 하나도 없을 수 있어 {@code List} 대신 {@link ObjectProvider} 로 받는다.
     * 빈 목록이면 주입 자체가 실패해 애플리케이션이 뜨지 않는다.
     */
    public ProviderUnlinkService(ObjectProvider<ProviderUnlinkClient> unlinkClients) {
        this.unlinkClients = unlinkClients.stream()
                .collect(Collectors.toMap(ProviderUnlinkClient::provider, Function.identity(),
                        (first, second) -> first, () -> new EnumMap<>(Provider.class)));
    }

    public void unlink(Provider provider, String providerId) {
        if (providerId == null || providerId.isBlank()) {
            // 이미 탈퇴해 회원번호를 비운 계정. 끊을 연결이 없다.
            return;
        }

        ProviderUnlinkClient unlinkClient = unlinkClients.get(provider);
        if (unlinkClient == null) {
            log.info("provider unlink not supported, skipped: provider={}", provider);
            return;
        }

        unlinkClient.unlink(providerId);
        log.info("provider unlinked: provider={}", provider);
    }
}
