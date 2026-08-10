package kr.givemeticket.api.login.ui;

import jakarta.validation.Valid;
import kr.givemeticket.api.global.auth.annotation.Provider;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.login.application.LoginService;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.login.domain.ProviderTokenProvider;
import kr.givemeticket.api.login.ui.apiSpec.LoginApiSpec;
import kr.givemeticket.api.login.ui.dto.AuthCodeRequest;
import kr.givemeticket.api.login.ui.dto.SignupRequest;
import kr.givemeticket.api.login.ui.dto.TokenResponse;
import kr.givemeticket.api.user.application.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LoginController implements LoginApiSpec {

    private final LoginService loginService;
    private final UserService userService;
    private final ProviderTokenProvider providerTokenProvider;

    @Override
    @BusinessLogging("인가 코드 검증")
    @PostMapping("/code")
    public ResponseEntity<TokenResponse> processCode(@Valid @RequestBody AuthCodeRequest request) {
        ProviderPrincipal providerPrincipal = loginService.authenticate(request.toAuthCodeCommand());

        TokenResponse response = providerTokenProvider.createToken(providerPrincipal);

        if (!userService.isUserExist(providerPrincipal.providerId(), providerPrincipal.provider())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(response);
        }
        return ResponseEntity.ok().body(response);
    }

    @Override
    @BusinessLogging("로그인")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Provider ProviderPrincipal providerPrincipal) {
        TokenResponse response = loginService.login(providerPrincipal);

        return ResponseEntity.ok().body(response);
    }

    @Override
    @BusinessLogging("회원가입")
    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest request,
                                                @Provider ProviderPrincipal providerPrincipal) {
        userService.createUser(request, providerPrincipal);

        TokenResponse response = loginService.login(providerPrincipal);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
