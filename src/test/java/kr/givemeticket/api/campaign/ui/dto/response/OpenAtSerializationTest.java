package kr.givemeticket.api.campaign.ui.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.ui.dto.response.ApplyResponse;
import kr.givemeticket.api.campaign.application.dto.response.CampaignDetailInfo;
import kr.givemeticket.api.campaign.ui.dto.request.PostCampaignRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * openAt 은 UTC 로 저장되고 Z 를 붙여 내려간다. 프론트가 {@code new Date(openAt)} 로 바로 파싱할 수 있어야 한다.
 *
 * <p>Z 가 붙는 건 부트가 WRITE_DATES_AS_TIMESTAMPS 를 꺼주기 때문이라, 직접 만든 ObjectMapper 가 아니라
 * 실제로 주입되는 자동 설정 빈으로 검증한다.
 */
class OpenAtSerializationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    @DisplayName("응답의 openAt 은 Z 가 붙은 ISO-8601 로 직렬화된다")
    void serializesOpenAtWithZuluSuffix() {
        CreateCampaignResponse response = new CreateCampaignResponse(
                1L, "3AbCdEfGh1", "테스트 행사", 100,
                LocalDateTime.of(2026, 8, 14, 10, 0).toInstant(ZoneOffset.UTC),
                false, null);

        contextRunner.run(context -> assertThat(
                context.getBean(ObjectMapper.class).writeValueAsString(response))
                .contains("\"openAt\":\"2026-08-14T10:00:00Z\""));
    }

    @Test
    @DisplayName("detail 의 eventAt/eventEndAt 도 같은 형식으로 내려간다")
    void serializesDetailDatesWithZuluSuffix() {
        CampaignDetailResponsePart detail = CampaignDetailResponsePart.from(new CampaignDetailInfo(
                "본문",
                LocalDateTime.of(2026, 9, 1, 19, 30),
                LocalDateTime.of(2026, 9, 1, 21, 0),
                "장소", "주소", "https://img", "연락처", 30000));

        contextRunner.run(context -> assertThat(
                context.getBean(ObjectMapper.class).writeValueAsString(detail))
                .contains("\"eventAt\":\"2026-09-01T19:30:00Z\"")
                .contains("\"eventEndAt\":\"2026-09-01T21:00:00Z\""));
    }

    @Test
    @DisplayName("null 인 시각은 그대로 null 로 내려간다")
    void keepsNullDatesNull() {
        ApplyResponse response = ApplyResponse.from(new ApplicationResponse(
                1L, 1L, 1L, ApplicationStatus.CONFIRMED, null, null, null, null));

        assertThat(response.expiresAt()).isNull();
    }

    @Test
    @DisplayName("요청의 openAt 은 Z 가 붙어 와도 UTC 로 역직렬화된다")
    void acceptsZuluSuffixOnRequest() {
        String json = """
                {"title":"테스트 행사","totalStock":100,"openAt":"2026-08-14T10:00:00Z","requiresPayment":false}
                """;

        contextRunner.run(context -> assertThat(
                context.getBean(ObjectMapper.class).readValue(json, PostCampaignRequest.class).openAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 0)));
    }
}
