package kr.givemeticket.api.login.ui;

import jakarta.validation.Valid;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.login.application.LoginService;
import kr.givemeticket.api.login.ui.apiSpec.LoginApiSpec;
import kr.givemeticket.api.login.ui.dto.AuthCodeRequest;
import kr.givemeticket.api.login.ui.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LoginController implements LoginApiSpec {

    private final LoginService loginService;

    @Override
    @BusinessLogging("소셜 로그인")
    @PostMapping("/code")
    public ResponseEntity<TokenResponse> processCode(@Valid @RequestBody AuthCodeRequest request) {
        return ResponseEntity.ok(loginService.login(request.toAuthCodeCommand()));
    }
}
