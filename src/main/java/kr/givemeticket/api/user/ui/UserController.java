package kr.givemeticket.api.user.ui;

import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.user.application.UserWithdrawService;
import kr.givemeticket.api.user.ui.apiSpec.UserApiSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApiSpec {

    private final UserWithdrawService userWithdrawService;

    @Override
    @BusinessLogging("회원 탈퇴")
    @DeleteMapping("users/me")
    public ResponseEntity<Void> withdraw(@LoginUserId Long userId) {
        userWithdrawService.withdraw(userId);

        return ResponseEntity.noContent().build();
    }
}
