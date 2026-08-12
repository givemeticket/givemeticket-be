package kr.givemeticket.api.login.infrastructure;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import kr.givemeticket.api.login.infrastructure.dto.KakaoErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;


@EnabledIfSystemProperty(named = "kakao.manual", matches = "true",
        disabledReason = "./gradlew kakaoLogin 으로만 실행한다")
class KakaoLoginManualTest {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";

    /** 운영에서 프론트가 실제로 보내는 값과 같아야 한다. 다르면 카카오가 KOE006 으로 거절한다. */
    private static final String DEFAULT_REDIRECT_URI = "http://localhost:5173/oauth/kakao";

    private static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(3);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    @DisplayName("브라우저 로그인 → 콜백 수신 → 토큰 교환까지 한 번에 확인한다")
    void loginEndToEnd() throws Exception {
        String restApiKey = requireConfig("KAKAO_REST_API_KEY");
        String clientSecret = config("KAKAO_CLIENT_SECRET");
        String redirectUri = configOrDefault("KAKAO_REDIRECT_URI", DEFAULT_REDIRECT_URI);

        printConfig(restApiKey, clientSecret, redirectUri);

        String code = System.getProperty("kakao.code");
        if (code == null || code.isBlank()) {
            code = receiveCodeFromBrowser(restApiKey, redirectUri);
        } else {
            System.out.println("\n[1/2] -Pcode 로 받은 인가 코드를 그대로 쓴다.");
            System.out.println("      code = " + code);
        }

        exchangeCode(code, restApiKey, clientSecret, redirectUri);
    }

    /**
     * 운영과 똑같이 RestClient 로 쏴서, 카카오가 준 에러 본문을 우리 코드가 실제로
     * 읽어 내는지 본다. 운영 로그의 "rejected without body" 가 카카오 탓인지
     * 우리 파싱 탓인지는 이 경로로만 갈린다.
     */
    @Test
    @DisplayName("운영 경로(RestClient)에서 카카오 에러 본문이 파싱되는지 확인한다")
    void errorBodyIsParsedOnProductionPath() {
        String restApiKey = requireConfig("KAKAO_REST_API_KEY");
        String clientSecret = config("KAKAO_CLIENT_SECRET");
        String redirectUri = configOrDefault("KAKAO_REDIRECT_URI", DEFAULT_REDIRECT_URI);

        RestClient restClient = RestClient.builder()
                .baseUrl("https://kauth.kakao.com")
                .build();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", "DUMMY_CODE_FOR_DIAGNOSIS");
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        try {
            restClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            System.out.println("\n      >> 거절되지 않았다. 이 테스트로는 확인할 수 없다.");

        } catch (HttpClientErrorException e) {
            KakaoErrorResponse parsed = e.getResponseBodyAs(KakaoErrorResponse.class);

            System.out.println("\n=== 운영 경로 에러 본문 파싱 ===");
            System.out.println("  status        = " + e.getStatusCode().value());
            System.out.println("  원문          = " + e.getResponseBodyAsString());
            System.out.println("  파싱 결과     = " + parsed);

            if (parsed == null) {
                System.out.println("  >> 카카오는 본문을 줬는데 우리 코드가 못 읽는다."
                        + " translateRejection 의 error == null 분기가 잘못 타는 원인이다.");
            } else {
                System.out.println("  >> 정상적으로 읽는다. 운영의 'without body' 로그는"
                        + " 본문이 실제로 비어 있던 다른 응답이다.");
            }
        }
    }

    /**
     * 인가 코드를 받으려면 로그인 후 리다이렉트를 누군가 받아야 한다.
     * 프론트를 띄우는 대신 redirect_uri 포트에 임시 서버를 열어 쿼리스트링만 주워 온다.
     */
    private String receiveCodeFromBrowser(String restApiKey, String redirectUri) throws Exception {
        URI callback = URI.create(redirectUri);
        int port = callback.getPort() == -1 ? 80 : callback.getPort();

        String authorizeUrl = AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + encode(restApiKey)
                + "&redirect_uri=" + encode(redirectUri);

        CompletableFuture<Map<String, String>> received = new CompletableFuture<>();
        HttpServer server = startCallbackServer(port, callback.getPath(), received);

        try {
            System.out.println("\n[1/2] 아래 주소를 브라우저에 붙여 넣고 카카오 로그인을 끝내세요.");
            System.out.println("      " + authorizeUrl);
            System.out.println("      리다이렉트는 이 테스트가 " + port + " 포트에서 직접 받습니다."
                    + " (프론트 dev 서버가 같은 포트를 쓰고 있으면 먼저 꺼야 합니다)");
            System.out.println("      최대 " + LOGIN_TIMEOUT.toMinutes() + "분 기다립니다...");

            Map<String, String> params = received.get(LOGIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            if (params.containsKey("error")) {
                // 토큰 교환 전에 인가 단계에서 이미 막힌 경우다. 대개 redirect_uri 미등록이다.
                throw new IllegalStateException("인가 단계에서 거절됨: error=" + params.get("error")
                        + ", description=" + params.get("error_description"));
            }

            String code = params.get("code");
            System.out.println("      인가 코드 수신: " + code);
            return code;

        } finally {
            server.stop(0);
        }
    }

    private HttpServer startCallbackServer(int port, String path,
                                           CompletableFuture<Map<String, String>> received) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // 경로를 가리지 않는다. 프론트 라우팅이 어떻든 코드만 건지면 된다.
        server.createContext("/", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());

            byte[] body = ("<html><meta charset=\"utf-8\"><body>"
                    + "<h3>인가 코드를 받았습니다. 터미널로 돌아가세요.</h3>"
                    + "</body></html>").getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();

            if (params.containsKey("code") || params.containsKey("error")) {
                received.complete(params);
            }
        });

        server.start();
        System.out.println("      콜백 대기 서버 시작: http://localhost:" + port + path);
        return server;
    }

    /**
     * 토큰 요청. 여기서는 Spring 변환을 태우지 않고 원문을 그대로 출력한다.
     * 운영 로그에 "rejected without body" 로 남는 응답이 정말 빈 본문인지 확인하려는 목적이다.
     */
    private void exchangeCode(String code, String restApiKey, String clientSecret, String redirectUri)
            throws Exception {

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", restApiKey);
        form.put("redirect_uri", redirectUri);
        form.put("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.put("client_secret", clientSecret);
        }

        System.out.println("\n[2/2] POST " + TOKEN_URL);
        System.out.println("      client_secret 전송 여부: " + form.containsKey("client_secret"));

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(urlEncoded(form)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n      status = " + response.statusCode());
        System.out.println("      content-type = " + response.headers().firstValue("content-type").orElse("(없음)"));
        System.out.println("      body = " + (response.body().isEmpty() ? "(빈 본문)" : response.body()));

        if (response.statusCode() == 200) {
            printIdTokenPayload(response.body());
        } else {
            printDiagnosis(response.statusCode(), response.body(), clientSecret);
        }
    }

    /**
     * id_token 은 서명 검증 전이라 신뢰할 값은 아니지만, aud 가 우리 앱 키인지
     * nickname 이 실려 오는지는 여기서 바로 보인다.
     */
    private void printIdTokenPayload(String tokenResponseBody) {
        int idTokenAt = tokenResponseBody.indexOf("\"id_token\"");
        if (idTokenAt < 0) {
            System.out.println("\n      >> 200 인데 id_token 이 없다. 콘솔에서 OpenID Connect 활성화가 빠졌다.");
            return;
        }

        String idToken = tokenResponseBody.substring(tokenResponseBody.indexOf('"', idTokenAt + 10) + 1);
        idToken = idToken.substring(0, idToken.indexOf('"'));

        String[] parts = idToken.split("\\.");
        System.out.println("\n      >> 로그인 성공. id_token payload:");
        System.out.println("         " + new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    /**
     * 상태 코드별로 손댈 곳이 다르다. 운영 코드가 4xx 를 전부 인가 코드 문제로
     * 뭉뚱그리는 바람에 놓쳤던 구분을 여기서는 그대로 남긴다.
     */
    private void printDiagnosis(int status, String body, String clientSecret) {
        System.out.println("\n      >> 진단:");

        if (status == 401) {
            System.out.println("         401 은 인가 코드가 아니라 client 인증 실패다.");
            if (clientSecret == null || clientSecret.isBlank()) {
                System.out.println("         지금 client_secret 을 보내지 않았다. 콘솔 > 보안 에서");
                System.out.println("         Client Secret 이 '사용함' 이면 이것만으로 401 이 난다.");
            } else {
                System.out.println("         client_secret 을 보냈는데도 거절됐다. REST API 키와");
                System.out.println("         시크릿이 같은 앱의 것인지 확인해야 한다.");
            }
        } else if (body.contains("KOE320")) {
            System.out.println("         KOE320 — 이미 썼거나 만료된 인가 코드다. 코드를 새로 받아야 한다.");
        } else if (body.contains("KOE006") || body.contains("KOE303")) {
            System.out.println("         redirect_uri 문제다. 콘솔에 등록된 Redirect URI 와");
            System.out.println("         지금 보낸 값이 완전히 같아야 한다(끝의 / 까지).");
        } else {
            System.out.println("         위 body 의 error_code 를 카카오 문서에서 확인한다.");
        }
    }

    private void printConfig(String restApiKey, String clientSecret, String redirectUri) {
        System.out.println("\n=== 설정 ===");
        System.out.println("  rest-api-key  = " + mask(restApiKey));
        System.out.println("  client-secret = " + (clientSecret == null || clientSecret.isBlank()
                ? "(비어 있음 — 콘솔에서 Client Secret 을 껐을 때만 정상)" : mask(clientSecret)));
        System.out.println("  redirect-uri  = " + redirectUri);
    }

    private String mask(String value) {
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private String urlEncoded(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String requireConfig(String key) {
        String value = config(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 가 비어 있다. 프로젝트 루트 .env 에 채워야 한다.");
        }
        return value;
    }

    private String configOrDefault(String key, String defaultValue) {
        String value = config(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String config(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return dotenv().get(key);
    }

    private Map<String, String> dotenv() {
        Path envFile = Path.of(System.getProperty("user.dir"), ".env");
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(envFile)) {
            return values;
        }
        try {
            List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                if (trimmed.startsWith("#") || eq <= 0) {
                    continue;
                }
                values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException(".env 를 읽지 못했다: " + envFile, e);
        }
        return values;
    }
}
