package kr.givemeticket.api.campaign.infrastructure;

import java.security.SecureRandom;
import kr.givemeticket.api.campaign.domain.ShortCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * 캠페인 id를 인코딩하지 않고 난수를 쓴다. id 기반이면 링크에서 다른 캠페인을 유추할 수 있고,
 * 아직 오픈하지 않은 행사가 노출된다.
 */
@Component
public class Base62ShortCodeGenerator implements ShortCodeGenerator {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder builder = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
