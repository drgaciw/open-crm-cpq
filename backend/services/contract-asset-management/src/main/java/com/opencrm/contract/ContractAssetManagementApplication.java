package com.opencrm.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.opencrm.contract", "com.opencrm.common"})
@EntityScan(basePackages = {"com.opencrm.contract", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.contract", "com.opencrm.common"})
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
public class ContractAssetManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractAssetManagementApplication.class, args);
    }
}
