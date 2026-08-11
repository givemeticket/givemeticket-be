package kr.givemeticket.api.login.domain;

/**
 * 소셜 인증으로 확인된 신원. 아직 우리 서비스의 유저는 아니다.
 *
 * <p>닉네임은 두 제공자 모두 선택 동의 항목이라 비어 올 수 있다.
 * 로그인을 막는 대신 여기서 대체값을 채워, 이후 계층은 닉네임이 항상 있다고 믿어도 된다.
 */
public record ProviderPrincipal(
        String providerId,
        Provider provider,
        String nickname,
        String profileImageUrl
) {

    private static final int SUFFIX_LENGTH = 4;

    public ProviderPrincipal {
        if (nickname == null || nickname.isBlank()) {
            nickname = defaultNickname(provider, providerId);
        }
    }

    /**
     * 전부 같은 이름이 되지 않도록 회원번호 끝자리를 붙인다.
     * 회원번호 일부라 되돌릴 수 없고, 화면에서 서로 구분만 되면 된다.
     */
    private static String defaultNickname(Provider provider, String providerId) {
        String suffix = (providerId.length() <= SUFFIX_LENGTH)
                ? providerId
                : providerId.substring(providerId.length() - SUFFIX_LENGTH);

        return provider.displayName() + "사용자" + suffix;
    }
}
