package kr.givemeticket.api.login.domain;

import java.util.Arrays;

public enum Provider {

    KAKAO("카카오"),
    NAVER("네이버");

    private final String displayName;

    Provider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Provider from(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> LoginException.unsupportedProvider(value));
    }
}
