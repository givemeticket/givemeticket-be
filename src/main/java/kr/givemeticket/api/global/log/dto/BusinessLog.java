package kr.givemeticket.api.global.log.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record BusinessLog(
        LogType logType,
        Long userId,
        String action,
        boolean success
) implements Log {

    public static BusinessLog success(Long userId, String action) {
        return new BusinessLog(LogType.BUSINESS, userId, action, true);
    }

    public static BusinessLog failure(Long userId, String action) {
        return new BusinessLog(LogType.BUSINESS, userId, action, false);
    }

    @Override
    public Map<String, Object> fields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("logType", logType.name());
        map.put("userId", userId);
        map.put("action", action);
        map.put("success", success);
        return map;
    }

    @Override
    public String summary() {
        String subject = (userId == null) ? "anonymous" : "user " + userId;
        String result = success ? "executed" : "failed";
        return String.format("[%s] %s %s %s", logType.name(), subject, result, action);
    }
}
