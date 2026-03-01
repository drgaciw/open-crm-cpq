package com.opencrm.cpq.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EntityScan(basePackages = {"com.opencrm.cpq.contract", "com.opencrm.cpq.shared"})
@EnableJpaRepositories(basePackages = {"com.opencrm.cpq.contract", "com.opencrm.cpq.shared"})
public class ContractAssetManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractAssetManagementApplication.class, args);
    }
}
