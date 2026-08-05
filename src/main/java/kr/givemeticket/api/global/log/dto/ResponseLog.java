package kr.givemeticket.api.global.log.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record ResponseLog(
        LogType logType,
        String method,
        String uri,
        int status,
        long durationMs
) implements Log {

    public static ResponseLog of(String method, String uri, int status, long durationMs) {
        return new ResponseLog(LogType.RESPONSE, method, uri, status, durationMs);
    }

    @Override
    public Map<String, Object> fields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("logType", logType.name());
        map.put("method", method);
        map.put("uri", uri);
        map.put("status", status);
        map.put("durationMs", durationMs);
        return map;
    }

    @Override
    public String summary() {
        return String.format("[%s] %s %s %d (%dms)", logType.name(), method, uri, status, durationMs);
    }
}
