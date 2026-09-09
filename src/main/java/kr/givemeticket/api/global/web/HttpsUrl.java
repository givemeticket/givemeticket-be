package kr.givemeticket.api.global.web;

/**
 * 외부에서 받은 URL 의 스킴을 https 로 올린다.
 *
 * <p>카카오는 프로필 사진에 동의하지 않았거나 올린 적 없는 계정에 기본 프로필 이미지를
 * {@code http://img1.kakaocdn.net/...} 으로 내려준다. 그대로 응답에 실으면 https 페이지에서
 * Mixed Content 경고가 뜬다. 브라우저가 알아서 승격시켜 이미지는 보이지만 콘솔이 지저분해진다.
 *
 * <p>같은 호스트가 https 를 받아주는 경우에만 의미가 있다. 카카오·네이버 CDN 은 둘 다 받는다.
 */
public final class HttpsUrl {

    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";

    private HttpsUrl() {
    }

    /**
     * @param url http 로 시작하면 https 로 바꾼 값, 아니면 받은 그대로. null 이면 null
     */
    public static String upgrade(String url) {
        if (url == null || !url.regionMatches(true, 0, HTTP_SCHEME, 0, HTTP_SCHEME.length())) {
            return url;
        }
        return HTTPS_SCHEME + url.substring(HTTP_SCHEME.length());
    }
}
