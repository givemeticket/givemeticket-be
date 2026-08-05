package kr.givemeticket.api.global.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import ch.qos.logback.core.status.Status;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * logback-*.xml 이 실제로 로드되는지 확인한다. logback 은 설정이 깨져도 예외를 던지지 않고
 * 조용히 status 로만 알리기 때문에, 오타가 나면 배포한 뒤 "로그가 안 남네" 로 발견된다.
 *
 * <p>각 파일은 logback-spring.xml 이 하는 것과 같은 방식(include)으로 로드한다.
 */
class LogbackConfigurationTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"logback-local.xml", "logback-dev.xml", "logback-prod.xml"})
    @DisplayName("프로파일별 logback 설정이 오류 없이 로드되고 appender 를 붙인다")
    void loadsWithoutError(String resource, @TempDir Path tempDir) throws JoranException {
        System.setProperty("LOGS_ROOT_PATH", tempDir.toString());
        LoggerContext context = new LoggerContext();
        try {
            String wrapper = "<configuration><include resource=\"%s\"/></configuration>".formatted(resource);

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(new ByteArrayInputStream(wrapper.getBytes(StandardCharsets.UTF_8)));

            List<Status> problems = context.getStatusManager().getCopyOfStatusList().stream()
                    .filter(status -> status.getLevel() != Status.INFO)
                    // 파일이 아닌 스트림에서 설정을 읽는 테스트라서 나오는 경고. 설정 자체와 무관하다.
                    .filter(status -> !(status.getOrigin() instanceof ConfigurationWatchListUtil))
                    .toList();

            assertThat(problems).as("logback status: %s", problems).isEmpty();
            assertThat(context.getLogger("ROOT").iteratorForAppenders()).hasNext();
        } finally {
            context.stop();
            System.clearProperty("LOGS_ROOT_PATH");
        }
    }
}
