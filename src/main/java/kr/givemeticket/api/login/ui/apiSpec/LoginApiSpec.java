package kr.givemeticket.api.login.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.login.ui.dto.AuthCodeRequest;
import kr.givemeticket.api.login.ui.dto.SignupRequest;
import kr.givemeticket.api.login.ui.dto.TokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "로그인 API", description = """
        소셜 로그인은 두 단계입니다.

        1. `/code` 로 인가 코드를 넘겨 제공자 토큰을 받습니다. 응답 상태가 가입 여부입니다
        2. 200이면 `/login`, 401이면 `/signup` 을 제공자 토큰으로 호출해 액세스 토큰을 받습니다

        이후 모든 API 는 `/login` 또는 `/signup` 이 준 액세스 토큰을 씁니다.
        """)
public interface LoginApiSpec {

    @Operation(summary = "인가 코드 검증",
            description = """
                    인가 코드로 제공자에게 신원을 확인하고 제공자 토큰을 발급합니다.
                    이 토큰은 로그인·회원가입에만 쓸 수 있고 만료가 짧습니다.

                    - 가입된 계정: 200 — `/login` 으로 진행합니다
                    - 가입 전 계정: 401 — `/signup` 으로 진행합니다.
                      두 경우 모두 body 의 토큰은 동일하게 쓸 수 있습니다
                    - 코드가 만료·재사용됐거나 redirectUrl 이 등록된 값과 다르면
                      400 INVALID_AUTHORIZATION_CODE
                    - 제공자 응답을 신뢰할 수 없으면 401 INVALID_ID_TOKEN,
                      제공자 호출 자체가 실패하면 502 LOGIN_PROVIDER_ERROR
                    """)
    @SecurityRequirements
    ResponseEntity<TokenResponse> processCode(@Valid @RequestBody AuthCodeRequest request);

    @Operation(summary = "로그인",
            description = """
                    제공자 토큰을 액세스 토큰으로 바꿉니다.

                    - 가입 기록이 없으면 404 USER_NOT_FOUND — `/signup` 을 호출해야 합니다
                    - 액세스 토큰으로 호출하면 401 TOKEN_TYPE_MISMATCH
                    """)
    @Parameter(name = "Authorization", description = "Bearer {/code 응답으로 받은 제공자 토큰}",
            in = ParameterIn.HEADER, required = true)
    @SecurityRequirements
    ResponseEntity<TokenResponse> login(@Parameter(hidden = true) ProviderPrincipal providerPrincipal);

    @Operation(summary = "회원가입",
            description = """
                    제공자 토큰으로 가입을 마치고 액세스 토큰을 받습니다.

                    - 제공자 회원번호는 토큰에서 꺼내므로 body 로 받지 않습니다
                    - 이미 가입된 계정이면 409 USER_ALREADY_REGISTERED
                    - 액세스 토큰으로 호출하면 401 TOKEN_TYPE_MISMATCH
                    """)
    @Parameter(name = "Authorization", description = "Bearer {/code 응답으로 받은 제공자 토큰}",
            in = ParameterIn.HEADER, required = true)
    @SecurityRequirements
    ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest request,
                                         @Parameter(hidden = true) ProviderPrincipal providerPrincipal);
}
