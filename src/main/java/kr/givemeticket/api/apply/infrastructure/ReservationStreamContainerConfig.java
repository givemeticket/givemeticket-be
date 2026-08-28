package kr.givemeticket.api.apply.infrastructure;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 워커가 도는 자리를 만든다.
 *
 * <p>별도 프로세스 없이 API 인스턴스 안에서 돌리되 <b>전용 스레드 풀로 가둔다</b> —
 * 워커가 DB 를 기다리며 잡은 스레드가 톰캣 요청 스레드로 번지면 큐를 둔 의미가 없다.
 * 풀 크기는 DB 커넥션 풀(기본 10)보다 작게 잡는다.
 *
 * <p>언제: 애플리케이션 기동 시 한 번.
 */
@Configuration
public class ReservationStreamContainerConfig {

    /** 워커 전용 스레드 풀. */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor reservationWorkerExecutor(
            @Value("${reservation.worker.threads:2}") int threads
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setThreadNamePrefix("reservation-worker-");
        // 종료 시 진행 중인 메시지는 끝내게 둔다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 메인 큐를 구독하는 리스너 컨테이너.
     *
     * <p>컨테이너는 빈 초기화 때 바로 폴링을 시작한다. {@code ApplicationRunner} 보다
     * 이르므로 <b>소비 그룹을 여기서 만들어야 한다</b> — 없는 그룹에 XREADGROUP 을 쏘면
     * 그 한 번으로 구독이 끊겨 워커가 영영 아무것도 집지 않는다.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
            reservationStreamContainer(
                    RedisConnectionFactory connectionFactory,
                    ThreadPoolTaskExecutor reservationWorkerExecutor,
                    ReservationStreamListener reservationStreamListener,
                    RedisReservationQueue reservationQueue,
                    ReservationQueueProperties properties,
                    @Value("${reservation.worker.batch-size:10}") int batchSize,
                    @Value("${reservation.worker.poll-timeout:2s}") Duration pollTimeout
            ) {
        reservationQueue.ensureConsumerGroup();

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .batchSize(batchSize)
                        .pollTimeout(pollTimeout)
                        .executor(reservationWorkerExecutor)
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        // ReadOffset.lastConsumed() = '>' — 이 그룹의 다른 소비자가 아직 가져가지 않은 것만.
        StreamReadRequest<String> request = StreamReadRequest
                .builder(StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()))
                .consumer(Consumer.from(properties.group(), consumerName()))
                // 자동 ack 는 쓰지 않는다. 저장에 성공했을 때만 리스너가 직접 ack 한다.
                .autoAcknowledge(false)
                // 기본값은 오류 한 번에 구독을 끊는다. Redis 가 잠깐 흔들렸다고 워커가
                // 영구히 멈추면, 재시작 전까지 예매가 큐에만 쌓인다.
                .cancelOnError(throwable -> false)
                .build();

        container.register(request, reservationStreamListener);
        return container;
    }

    /** 같은 그룹 안에서 인스턴스를 구분하는 이름. 처리 중 목록이 이 이름으로 묶인다. */
    private String consumerName() {
        String host = System.getenv().getOrDefault("HOSTNAME", "local");
        return "worker-" + host + "-" + ProcessHandle.current().pid();
    }
}
