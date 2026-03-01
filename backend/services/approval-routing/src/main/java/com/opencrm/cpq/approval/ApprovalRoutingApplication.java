package com.opencrm.cpq.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EntityScan(basePackages = {"com.opencrm.cpq.approval", "com.opencrm.cpq.shared"})
@EnableJpaRepositories(basePackages = {"com.opencrm.cpq.approval", "com.opencrm.cpq.shared"})
public class ApprovalRoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalRoutingApplication.class, args);
    }
}
