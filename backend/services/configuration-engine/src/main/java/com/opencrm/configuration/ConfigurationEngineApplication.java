package com.opencrm.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = {"com.opencrm.configuration", "com.opencrm.common"})
@EntityScan(basePackages = {"com.opencrm.configuration", "com.opencrm.common"})
@EnableJpaAuditing
@EnableCaching
@EnableKafka
public class ConfigurationEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigurationEngineApplication.class, args);
    }
}
