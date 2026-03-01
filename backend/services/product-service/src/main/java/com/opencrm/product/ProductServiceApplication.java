package com.opencrm.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.opencrm.product", "com.opencrm.common", "com.opencrm.security"})
@EntityScan(basePackages = {"com.opencrm.product", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.product", "com.opencrm.common"})
@EnableJpaAuditing
@EnableScheduling
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
