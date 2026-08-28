package kr.givemeticket.api.admin.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 어드민 API 의 유일한 관문. 사용자 도메인에 역할 개념이 없어 공유 키를 쓴다.
 *
 * <p>누가 눌렀는지 남지 않으므로 <b>되돌릴 수 있는 조작에만</b> 쓴다.
 * 키가 비면 <b>엔드포인트가 아예 열리지 않는다</b> — 설정을 잊었을 때 안전한 쪽이다.
 *
 * <p>언제: 어드민 엔드포인트 진입 시 맨 앞에서.
 */
@Slf4j
@Component
public class AdminAccessGuard {

    private final byte[] expectedKey;

    public AdminAccessGuard(@Value("${admin.api-key:}") String apiKey) {
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        if (apiKey.isBlank()) {
            log.warn("admin.api-key 가 비어 있습니다. 어드민 API 는 열리지 않습니다.");
        }
    }

    /** 헤더로 받은 키를 검사한다. 통과하지 못하면 예외를 던진다. */
    public void verify(String presentedKey) {
        if (expectedKey.length == 0) {
            throw AdminAccessException.notEnabled();
        }
        if (presentedKey == null || !matches(presentedKey)) {
            throw AdminAccessException.invalidKey();
        }
    }

    /** 상수 시간 비교. 앞자리부터 끊으면 응답 시간 차이로 키가 새어 나간다. */
    private boolean matches(String presentedKey) {
        return MessageDigest.isEqual(
                presentedKey.getBytes(StandardCharsets.UTF_8), expectedKey);
    }
}
