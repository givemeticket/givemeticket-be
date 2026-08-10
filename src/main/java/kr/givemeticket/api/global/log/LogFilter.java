package kr.givemeticket.api.global.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.givemeticket.api.global.auth.AccessTokenProvider;
import kr.givemeticket.api.global.auth.BearerToken;
import kr.givemeticket.api.global.log.dto.RequestLog;
import kr.givemeticket.api.global.log.dto.ResponseLog;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.Markers;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 모든 요청에 추적용 MDC 를 심고 요청/응답 로그를 남긴다.
 *
 * <p>MDC 에 넣은 값은 JSON 로그의 모든 라인에 자동으로 붙기 때문에,
 * Loki 에서 {@code request_id} 하나로 한 요청이 남긴 모든 로그를 모을 수 있다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LogFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "request_id";
    private static final String REQUEST_URI = "request_uri";
    private static final String CLIENT_IP = "client_ip";
    private static final String USER_ID = "user_id";
    private static final List<String> FORWARDED_FOR_HEADERS = List.of("X-Forwarded-For", "X-Real-IP");

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final SensitiveDataMasker masker;
    private final AccessTokenProvider accessTokenProvider;
    private final List<String> excludePatterns;

    public LogFilter(SensitiveDataMasker masker, AccessTokenProvider accessTokenProvider,
                     LogProperties properties) {
        this.masker = masker;
        this.accessTokenProvider = accessTokenProvider;
        this.excludePatterns = properties.excludePatterns();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return excludePatterns.stream().anyMatch(pattern -> antPathMatcher.match(pattern, uri));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        String requestUri = request.getRequestURI();
        putTraceContext(request, requestUri);
        long startedAt = System.currentTimeMillis();

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            // body 는 요청이 실제로 읽힌 뒤에야 캐시에 들어차므로 체인 이후에 꺼낸다.
            logRequest(cachedRequest, requestUri);
            logResponse(cachedResponse, request.getMethod(), requestUri, System.currentTimeMillis() - startedAt);

            cachedResponse.copyBodyToResponse();
            MDC.clear();
        }
    }

    private void putTraceContext(HttpServletRequest request, String requestUri) {
        MDC.put(REQUEST_ID, UUID.randomUUID().toString().substring(0, 8));
        MDC.put(REQUEST_URI, requestUri);
        MDC.put(CLIENT_IP, resolveClientIp(request));

        resolveUserId(request).ifPresent(userId -> MDC.put(USER_ID, String.valueOf(userId)));
    }

    /**
     * 토큰이 없거나 잘못됐어도 로그는 남아야 한다.
     * 인증 실패 자체는 ArgumentResolver 가 401 로 처리하므로 여기서는 조용히 넘긴다.
     */
    private Optional<Long> resolveUserId(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            return Optional.empty();
        }

        try {
            return Optional.of(accessTokenProvider.extractUserId(BearerToken.resolve(header)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * nginx 를 거치므로 remoteAddr 은 프록시 IP 다. 포워딩 헤더를 먼저 본다.
     */
    private String resolveClientIp(HttpServletRequest request) {
        for (String header : FORWARDED_FOR_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void logRequest(ContentCachingRequestWrapper request, String requestUri) {
        String body = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);
        RequestLog requestLog = RequestLog.of(
                request.getMethod(),
                requestUri,
                request.getQueryString(),
                masker.mask(request.getContentType(), body)
        );
        log.info(Markers.appendEntries(requestLog.fields()), requestLog.summary());
    }

    private void logResponse(ContentCachingResponseWrapper response, String method, String requestUri,
                             long durationMs) {
        ResponseLog responseLog = ResponseLog.of(method, requestUri, response.getStatus(), durationMs);
        log.info(Markers.appendEntries(responseLog.fields()), responseLog.summary());
    }
}
