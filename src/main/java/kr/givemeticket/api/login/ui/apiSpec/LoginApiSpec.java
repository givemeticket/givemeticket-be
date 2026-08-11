package kr.givemeticket.api.login.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.givemeticket.api.login.ui.dto.AuthCodeRequest;
import kr.givemeticket.api.login.ui.dto.TokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "로그인 API", description = """
        소셜 로그인은 호출 한 번으로 끝납니다.
        인가 코드를 넘기면 액세스 토큰이 나오고, 이후 모든 API 는 그 토큰을 씁니다.
        """)
public interface LoginApiSpec {

    @Operation(summary = "소셜 로그인",
            description = """
                    인가 코드로 제공자에게 신원을 확인하고 액세스 토큰을 발급합니다.

                    - 처음 온 계정이면 이 호출에서 가입까지 끝납니다. 닉네임과 프로필 이미지는
                      제공자가 준 값을 그대로 쓰기 때문에 따로 받을 것이 없습니다
                    - 이미 가입한 계정의 닉네임은 덮어쓰지 않습니다
                    - 코드가 만료·재사용됐거나 redirectUrl / state 가 어긋나면
                      400 INVALID_AUTHORIZATION_CODE
                    - 제공자 응답을 신뢰할 수 없으면 401 INVALID_ID_TOKEN,
                      제공자 호출 자체가 실패하면 502 LOGIN_PROVIDER_ERROR
                    """)
    @SecurityRequirements
    ResponseEntity<TokenResponse> processCode(@Valid @RequestBody AuthCodeRequest request);
}
