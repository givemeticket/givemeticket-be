package kr.givemeticket.api.login.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.givemeticket.api.global.auth.AccessTokenProvider;
import kr.givemeticket.api.login.domain.AuthCodeCommand;
import kr.givemeticket.api.login.domain.LoginClient;
import kr.givemeticket.api.login.domain.LoginException;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.login.ui.dto.TokenResponse;
import kr.givemeticket.api.user.application.UserService;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final Map<Provider, LoginClient> loginClients;
    private final UserService userService;
    private final AccessTokenProvider accessTokenProvider;

    public LoginService(List<LoginClient> loginClients,
                        UserService userService,
                        AccessTokenProvider accessTokenProvider) {
        this.loginClients = loginClients.stream()
                .collect(Collectors.toMap(LoginClient::provider, Function.identity(),
                        (first, second) -> first, () -> new EnumMap<>(Provider.class)));
        this.userService = userService;
        this.accessTokenProvider = accessTokenProvider;
    }

    /**
     * 인가 코드를 제공자에게 넘겨 신원만 확인한다. 가입 여부는 여기서 따지지 않는다.
     */
    public ProviderPrincipal authenticate(AuthCodeCommand command) {
        LoginClient loginClient = loginClients.get(command.provider());
        if (loginClient == null) {
            // enum 에는 있는데 클라이언트 구현이 아직 없는 제공자.
            throw LoginException.unsupportedProvider(command.provider().name());
        }

        return new ProviderPrincipal(loginClient.fetchProviderId(command), command.provider());
    }

    /**
     * 신원 확인은 제공자 토큰이 이미 끝냈다. 여기서는 우리 서비스의 userId 로 바꿔 액세스 토큰을 발급한다.
     */
    public TokenResponse login(ProviderPrincipal providerPrincipal) {
        Long userId = userService.getUserId(providerPrincipal.providerId(), providerPrincipal.provider());

        return TokenResponse.from(accessTokenProvider.createToken(userId));
    }
}
