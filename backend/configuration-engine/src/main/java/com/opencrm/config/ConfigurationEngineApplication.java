package com.opencrm.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.opencrm.config.domain", "com.opencrm.shared.outbox"})
@EnableJpaRepositories(basePackages = {"com.opencrm.config.repository"})
public class ConfigurationEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigurationEngineApplication.class, args);
    }
}
