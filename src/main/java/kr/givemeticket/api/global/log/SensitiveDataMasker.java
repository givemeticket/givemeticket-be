package kr.givemeticket.api.global.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 요청 body 를 로그에 남기기 전에 민감 필드를 가린다.
 *
 * <p>JSON 이 아닌 body(multipart, form, binary)는 아예 기록하지 않는다.
 * 파싱에 실패하면 원문을 흘리지 않고 자리표시자만 남긴다 —
 * "못 읽은 body 를 그대로 남기는" 게 가장 위험한 실패 모드라서다.
 */
@Component
public class SensitiveDataMasker {

    private static final String MASK = "****";
    private static final String EMPTY = "";

    private final ObjectMapper objectMapper;
    private final List<String> maskedKeys;
    private final int maxBodyLength;

    public SensitiveDataMasker(ObjectMapper objectMapper, LogProperties properties) {
        this.objectMapper = objectMapper;
        this.maskedKeys = properties.maskedKeys().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .toList();
        this.maxBodyLength = properties.maxBodyLength();
    }

    public String mask(String contentType, String body) {
        if (!StringUtils.hasText(body)) {
            return EMPTY;
        }
        if (!isJson(contentType)) {
            return String.format("[%s body omitted]", StringUtils.hasText(contentType) ? contentType : "unknown");
        }
        if (body.length() > maxBodyLength) {
            return String.format("[body too large: %d chars]", body.length());
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            maskNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return "[unparsable body]";
        }
    }

    private boolean isJson(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                    || mediaType.getSubtype().endsWith("+json");
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    private void maskNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                if (isSensitive(fieldName)) {
                    objectNode.put(fieldName, MASK);
                    continue;
                }
                maskNode(objectNode.get(fieldName));
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskNode);
        }
    }

    private boolean isSensitive(String key) {
        String lowered = key.toLowerCase(Locale.ROOT);
        return maskedKeys.stream().anyMatch(lowered::contains);
    }
}
