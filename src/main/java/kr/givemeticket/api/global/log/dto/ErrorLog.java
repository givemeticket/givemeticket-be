package kr.givemeticket.api.global.log.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record ErrorLog(
        LogType logType,
        int status,
        String code,
        String message,
        String exceptionType,
        String stackTrace
) implements Log {

    private static final int STACK_TRACE_DEPTH = 5;
    private static final String NO_TRACE = "No trace for client error";

    /**
     * 서버 책임의 오류. 스택트레이스를 남긴다.
     */
    public static ErrorLog serverError(int status, Throwable e, String code) {
        return new ErrorLog(LogType.SERVER_ERROR, status, code, e.getMessage(),
                e.getClass().getName(), shortenStackTrace(e));
    }

    /**
     * 소셜 로그인 제공자 등 외부 시스템 호출 실패.
     */
    public static ErrorLog externalError(int status, Throwable e, String code) {
        return new ErrorLog(LogType.EXTERNAL_ERROR, status, code, e.getMessage(),
                e.getClass().getName(), shortenStackTrace(e));
    }

    /**
     * 클라이언트 잘못으로 인한 4xx. 스택트레이스는 노이즈라 남기지 않는다.
     */
    public static ErrorLog clientError(int status, Throwable e, String code) {
        return new ErrorLog(LogType.CLIENT_ERROR, status, code, e.getMessage(),
                e.getClass().getName(), NO_TRACE);
    }

    private static String shortenStackTrace(Throwable e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(stackTrace.length, STACK_TRACE_DEPTH);
        for (int i = 0; i < limit; i++) {
            sb.append(stackTrace[i]).append('\n');
        }
        return sb.toString();
    }

    @Override
    public Map<String, Object> fields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("logType", logType.name());
        map.put("status", status);
        map.put("code", code);
        map.put("message", message);
        map.put("exceptionType", exceptionType);
        map.put("stackTrace", stackTrace);
        return map;
    }

    @Override
    public String summary() {
        return String.format("[%s] %d %s occurred", logType.name(), status, code);
    }
}
