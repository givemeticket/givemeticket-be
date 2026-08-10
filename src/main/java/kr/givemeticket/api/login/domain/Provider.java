package kr.givemeticket.api.login.domain;

import java.util.Arrays;

public enum Provider {

    KAKAO,
    NAVER;

    public static Provider from(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> LoginException.unsupportedProvider(value));
    }
}
