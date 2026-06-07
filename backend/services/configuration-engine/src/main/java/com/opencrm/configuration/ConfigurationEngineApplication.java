package com.opencrm.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.opencrm.configuration", "com.opencrm.common"})
@EntityScan(basePackages = {"com.opencrm.configuration", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.configuration", "com.opencrm.common"})
@EnableJpaAuditing
@EnableCaching
@EnableKafka
@EnableScheduling
public class ConfigurationEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigurationEngineApplication.class, args);
    }
}
