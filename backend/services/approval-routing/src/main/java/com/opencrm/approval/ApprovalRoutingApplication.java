package com.opencrm.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = {"com.opencrm.approval", "com.opencrm.common"})
@EntityScan(basePackages = {"com.opencrm.approval", "com.opencrm.common"})
@EnableJpaAuditing
@EnableKafka
public class ApprovalRoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalRoutingApplication.class, args);
    }
}
