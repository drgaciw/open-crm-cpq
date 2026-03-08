package com.opencrm.dealdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(scanBasePackages = {
        "com.opencrm.dealdesk",
        "com.opencrm.common",
        "com.opencrm.security",
        "com.opencrm.rules"
})
@EnableJpaAuditing
@EnableRetry
@EnableCaching
public class DealDeskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DealDeskServiceApplication.class, args);
    }
}
