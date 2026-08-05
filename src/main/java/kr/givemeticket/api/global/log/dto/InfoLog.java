package kr.givemeticket.api.global.log.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 위 타입 어디에도 속하지 않는 임의의 이벤트 로그.
 */
public record InfoLog(
        LogType logType,
        String event,
        String message,
        Map<String, Object> attributes
) implements Log {

    public static InfoLog of(String event, String message) {
        return new InfoLog(LogType.INFO, event, message, Map.of());
    }

    public static InfoLog of(String event, String message, Map<String, Object> attributes) {
        return new InfoLog(LogType.INFO, event, message, attributes);
    }

    @Override
    public Map<String, Object> fields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("logType", logType.name());
        map.put("event", event);
        map.put("message", message);
        map.putAll(attributes);
        return map;
    }

    @Override
    public String summary() {
        return String.format("[%s:%s] %s", logType.name(), event, message);
    }
}
