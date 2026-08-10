package kr.givemeticket.api.global.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import kr.givemeticket.api.global.auth.AccessTokenProvider;
import kr.givemeticket.api.global.auth.JwtProvider;
import net.logstash.logback.marker.LogstashMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class LogFilterTest {

    private static final String SECRET = "log-filter-test-secret-key-at-least-32-bytes";

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private AccessTokenProvider accessTokenProvider;

    @RestController
    static class StubController {

        @PostMapping("/api/v1/echo")
        String echo(@RequestBody String body) {
            return body;
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
    }

    @BeforeEach
    void setUp() {
        accessTokenProvider = new AccessTokenProvider(new JwtProvider(SECRET), 60_000);

        LogFilter filter = new LogFilter(
                new SensitiveDataMasker(new ObjectMapper(), new LogProperties(null, null, 0)),
                accessTokenProvider,
                new LogProperties(null, null, 0)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(filter)
                .build();

        logger = (Logger) LoggerFactory.getLogger(LogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("요청/응답 로그를 각각 남기고 body 는 마스킹된다")
    void logsRequestAndResponse() throws Exception {
        mockMvc.perform(post("/api/v1/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenProvider.createToken(42L))
                        .content("{\"campaignId\":7,\"password\":\"secret\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(2);

        Map<String, Object> request = fieldsOf(events.get(0));
        assertThat(request).containsEntry("logType", "REQUEST")
                .containsEntry("method", "POST")
                .containsEntry("uri", "/api/v1/echo");
        assertThat(request.get("body").toString())
                .contains("\"campaignId\":7")
                .contains("\"password\":\"****\"")
                .doesNotContain("secret");

        Map<String, Object> response = fieldsOf(events.get(1));
        assertThat(response).containsEntry("logType", "RESPONSE")
                .containsEntry("status", 200);
        assertThat(((Number) response.get("durationMs")).longValue()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("응답 body 는 필터를 거쳐도 그대로 클라이언트에 전달된다")
    void copiesResponseBodyBack() throws Exception {
        mockMvc.perform(post("/api/v1/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campaignId\":7}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"campaignId\":7}"));
    }

    @Test
    @DisplayName("actuator 등 제외 경로는 로그를 남기지 않는다")
    void skipsExcludedPaths() throws Exception {
        mockMvc.perform(get("/actuator/health"));

        assertThat(appender.list).isEmpty();
    }

    /**
     * 마커가 실제로 JSON 최상위 필드로 펼쳐지는지까지 확인하려고 인코더와 같은 방식으로 직렬화한다.
     */
    private Map<String, Object> fieldsOf(ILoggingEvent event) throws IOException {
        LogstashMarker marker = (LogstashMarker) event.getMarkerList().get(0);
        ObjectMapper mapper = new ObjectMapper();
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = mapper.getFactory().createGenerator(writer)) {
            generator.writeStartObject();
            marker.writeTo(generator);
            generator.writeEndObject();
        }
        return mapper.readValue(writer.toString(), new TypeReference<>() {
        });
    }
}
