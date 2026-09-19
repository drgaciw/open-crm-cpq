package com.opencrm.pricebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.opencrm.pricebook", "com.opencrm.common", "com.opencrm.security"})
@EntityScan(basePackages = {"com.opencrm.pricebook", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.pricebook", "com.opencrm.common.event"})
@EnableJpaAuditing
public class PriceBookServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceBookServiceApplication.class, args);
    }
}
