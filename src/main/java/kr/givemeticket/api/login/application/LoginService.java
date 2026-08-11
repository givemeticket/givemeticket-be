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
     * 인가 코드 하나로 로그인을 끝낸다.
     *
     * <p>닉네임을 제공자에게서 받아오므로 사용자에게 더 물어볼 것이 없다.
     * 가입 여부로 흐름을 나누지 않고, 처음 온 계정이면 그 자리에서 가입까지 끝낸다.
     */
    public TokenResponse login(AuthCodeCommand command) {
        ProviderPrincipal principal = authenticate(command);
        Long userId = userService.getOrCreateUserId(principal);

        return TokenResponse.from(accessTokenProvider.createToken(userId));
    }

    private ProviderPrincipal authenticate(AuthCodeCommand command) {
        LoginClient loginClient = loginClients.get(command.provider());
        if (loginClient == null) {
            // enum 에는 있는데 클라이언트 구현이 아직 없는 제공자.
            throw LoginException.unsupportedProvider(command.provider().name());
        }

        return loginClient.fetchPrincipal(command);
    }
}
