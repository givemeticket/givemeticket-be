package kr.givemeticket.api.user.ui;

import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.user.application.UserService;
import kr.givemeticket.api.user.application.UserWithdrawService;
import kr.givemeticket.api.user.ui.apiSpec.UserApiSpec;
import kr.givemeticket.api.user.ui.dto.response.GetUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApiSpec {

    private final UserService userService;
    private final UserWithdrawService userWithdrawService;

    @Override
    @GetMapping("users/{userId}")
    public ResponseEntity<GetUserResponse> readUser(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(GetUserResponse.from(userService.getUser(userId)));
    }

    @Override
    @BusinessLogging("회원 탈퇴")
    @DeleteMapping("users/me")
    public ResponseEntity<Void> withdraw(@LoginUserId Long userId) {
        userWithdrawService.withdraw(userId);

        return ResponseEntity.noContent().build();
    }
}
