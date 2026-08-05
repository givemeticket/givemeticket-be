package kr.givemeticket.api.global.log.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record RequestLog(
        LogType logType,
        String method,
        String uri,
        String query,
        String body
) implements Log {

    public static RequestLog of(String method, String uri, String query, String body) {
        return new RequestLog(LogType.REQUEST, method, uri, query, body);
    }

    @Override
    public Map<String, Object> fields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("logType", logType.name());
        map.put("method", method);
        map.put("uri", uri);
        map.put("query", query);
        map.put("body", body);
        return map;
    }

    @Override
    public String summary() {
        return String.format("[%s] %s %s", logType.name(), method, uri);
    }
}
