package kr.givemeticket.api.global.log;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SensitiveDataMaskerTest {

    private SensitiveDataMasker masker;

    @BeforeEach
    void setUp() {
        masker = new SensitiveDataMasker(new ObjectMapper(), new LogProperties(null, null, 0));
    }

    @Test
    @DisplayName("민감 키의 값은 마스킹하고 나머지는 그대로 둔다")
    void masksSensitiveKeys() {
        String body = """
                {"campaignId":1,"cardNumber":"4111-1111-1111-1111","email":"a@b.com"}
                """;

        String masked = masker.mask(MediaType.APPLICATION_JSON_VALUE, body);

        assertThat(masked).contains("\"campaignId\":1");
        assertThat(masked).doesNotContain("4111").doesNotContain("a@b.com");
        assertThat(masked).contains("\"cardNumber\":\"****\"", "\"email\":\"****\"");
    }

    @Test
    @DisplayName("중첩 객체와 배열 안쪽까지 마스킹한다")
    void masksNestedValues() {
        String body = """
                {"payments":[{"amount":1000,"card":{"number":"4111","cvc":"123"}}],"user":{"password":"pw"}}
                """;

        String masked = masker.mask(MediaType.APPLICATION_JSON_VALUE, body);

        assertThat(masked).doesNotContain("4111").doesNotContain("123").doesNotContain("\"pw\"");
        assertThat(masked).contains("\"amount\":1000");
    }

    @Test
    @DisplayName("JSON 이 아닌 body 는 기록하지 않는다")
    void omitsNonJsonBody() {
        String masked = masker.mask(MediaType.MULTIPART_FORM_DATA_VALUE, "----boundary\r\nbinary junk");

        assertThat(masked).isEqualTo("[multipart/form-data body omitted]");
    }

    @Test
    @DisplayName("파싱에 실패해도 원문을 흘리지 않는다")
    void neverLeaksUnparsableBody() {
        String masked = masker.mask(MediaType.APPLICATION_JSON_VALUE, "{\"password\": \"pw\", broken");

        assertThat(masked).isEqualTo("[unparsable body]");
    }

    @Test
    @DisplayName("한계 길이를 넘는 body 는 크기만 남긴다")
    void skipsOversizedBody() {
        SensitiveDataMasker smallLimit =
                new SensitiveDataMasker(new ObjectMapper(), new LogProperties(null, null, 10));

        String masked = smallLimit.mask(MediaType.APPLICATION_JSON_VALUE, "{\"a\":\"012345678901234\"}");

        assertThat(masked).startsWith("[body too large:");
    }

    @Test
    @DisplayName("빈 body 는 빈 문자열이다")
    void emptyBody() {
        assertThat(masker.mask(MediaType.APPLICATION_JSON_VALUE, "")).isEmpty();
        assertThat(masker.mask(null, null)).isEmpty();
    }

    @Test
    @DisplayName("마스킹 키는 설정으로 덮어쓸 수 있다")
    void masksConfiguredKeysOnly() {
        SensitiveDataMasker custom =
                new SensitiveDataMasker(new ObjectMapper(), new LogProperties(null, List.of("nickname"), 0));

        String masked = custom.mask(MediaType.APPLICATION_JSON_VALUE, "{\"nickname\":\"민기\",\"password\":\"pw\"}");

        assertThat(masked).contains("\"nickname\":\"****\"").contains("\"password\":\"pw\"");
    }
}
