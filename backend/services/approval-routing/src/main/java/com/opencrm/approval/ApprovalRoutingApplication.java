package com.opencrm.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.opencrm.approval", "com.opencrm.common"})
@EntityScan(basePackages = {"com.opencrm.approval", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.approval", "com.opencrm.common"})
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
public class ApprovalRoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalRoutingApplication.class, args);
    }
}
