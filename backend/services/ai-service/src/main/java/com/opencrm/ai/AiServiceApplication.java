package com.opencrm.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// Shared DTOs need no component scan; common outbox components belong to domain services.
@ComponentScan(basePackages = {"com.opencrm.ai", "com.opencrm.security"})
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
