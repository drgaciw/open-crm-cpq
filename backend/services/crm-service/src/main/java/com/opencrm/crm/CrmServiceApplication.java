package com.opencrm.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CRM Service - Core CRM functionality.
 *
 * Manages:
 * - Contacts and Organizations
 * - Leads with lifecycle tracking
 * - Deals/Opportunities with pipeline stages
 * <p>
 * Extended activities (tasks, calls, notes) are planned; the API gateway does not route
 * {@code /api/v1/activities} to this service until that slice is implemented.
 */
@SpringBootApplication
@EnableJpaAuditing
// Runs the daily processed_events purge registered by libs/common's KafkaInboxAutoConfiguration (ADR-018 D8).
@EnableScheduling
public class CrmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmServiceApplication.class, args);
    }
}
