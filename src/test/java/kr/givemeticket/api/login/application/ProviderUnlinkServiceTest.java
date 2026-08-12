package kr.givemeticket.api.login.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderUnlinkClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProviderUnlinkServiceTest {

    /**
     * 어떤 회원번호로 불렸는지만 기록한다.
     */
    private static class RecordingUnlinkClient implements ProviderUnlinkClient {

        private final Provider provider;
        private final List<String> unlinked = new java.util.ArrayList<>();

        private RecordingUnlinkClient(Provider provider) {
            this.provider = provider;
        }

        @Override
        public Provider provider() {
            return provider;
        }

        @Override
        public void unlink(String providerId) {
            unlinked.add(providerId);
        }
    }

    /**
     * 구현체가 하나도 없는 상황까지 테스트해야 해서 ObjectProvider 를 직접 만든다.
     * 서비스는 stream() 만 쓴다.
     */
    private static ObjectProvider<ProviderUnlinkClient> providerOf(ProviderUnlinkClient... clients) {
        return new ObjectProvider<>() {

            @Override
            public Stream<ProviderUnlinkClient> stream() {
                return Stream.of(clients);
            }

            @Override
            public ProviderUnlinkClient getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ProviderUnlinkClient getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ProviderUnlinkClient getIfAvailable() {
                return null;
            }

            @Override
            public ProviderUnlinkClient getIfUnique() {
                return null;
            }
        };
    }

    @Test
    @DisplayName("제공자에 맞는 클라이언트로 연결을 끊는다")
    void unlinksWithMatchingClient() {
        RecordingUnlinkClient kakao = new RecordingUnlinkClient(Provider.KAKAO);
        ProviderUnlinkService service = new ProviderUnlinkService(providerOf(kakao));

        service.unlink(Provider.KAKAO, "1234567890");

        assertThat(kakao.unlinked).containsExactly("1234567890");
    }

    /**
     * 네이버는 사용자의 액세스 토큰이 있어야 끊을 수 있는데 우리는 보관하지 않는다.
     * 구현이 없다고 탈퇴가 막히면 안 된다.
     */
    @Test
    @DisplayName("구현이 없는 제공자는 건너뛴다")
    void skipsProviderWithoutClient() {
        RecordingUnlinkClient kakao = new RecordingUnlinkClient(Provider.KAKAO);
        ProviderUnlinkService service = new ProviderUnlinkService(providerOf(kakao));

        service.unlink(Provider.NAVER, "naver-id");

        assertThat(kakao.unlinked).isEmpty();
    }

    @Test
    @DisplayName("구현체가 하나도 없어도 동작한다")
    void worksWithoutAnyClient() {
        ProviderUnlinkService service = new ProviderUnlinkService(providerOf());

        service.unlink(Provider.KAKAO, "1234567890");
    }

    @Test
    @DisplayName("회원번호가 비어 있으면 부르지 않는다")
    void skipsWhenProviderIdIsBlank() {
        RecordingUnlinkClient kakao = new RecordingUnlinkClient(Provider.KAKAO);
        ProviderUnlinkService service = new ProviderUnlinkService(providerOf(kakao));

        service.unlink(Provider.KAKAO, null);
        service.unlink(Provider.KAKAO, "  ");

        assertThat(kakao.unlinked).isEmpty();
    }
}
