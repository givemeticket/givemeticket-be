package kr.givemeticket.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(FaultProperties.class)
@SpringBootApplication
public class PaymentMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentMockApplication.class, args);
    }
}
