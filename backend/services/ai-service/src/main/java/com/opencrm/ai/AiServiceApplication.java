package com.opencrm.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scans {@code com.opencrm.ai} only. Shared DTOs need no component scan, common outbox components belong to domain
 * services, and {@code libs/security} arrives as auto-configuration: the ADR-016 baseline chain (decision 9) and the
 * event actor resolver. Scanning {@code com.opencrm.security} would register those auto-configurations as ordinary
 * configuration classes, bypassing their ordering.
 */
@SpringBootApplication
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
