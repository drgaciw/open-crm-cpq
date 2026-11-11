package com.opencrm.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CRM Service - Core CRM functionality.
 *
 * Manages:
 * - Contacts and Organizations
 * - Leads with lifecycle tracking
 * - Deals/Opportunities with pipeline stages
 * - Activity timeline notes and tasks ({@code /api/v1/activities}). Email and meeting
 *   rows are reserved for mailbox ingestion (DRG-521) and are not created here.
 */
@SpringBootApplication(scanBasePackages = {"com.opencrm.crm", "com.opencrm.common"})
@EntityScan(basePackages = {"com.opencrm.crm", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.crm", "com.opencrm.common"})
@EnableJpaAuditing
// Runs the daily processed_events purge registered by libs/common's KafkaInboxAutoConfiguration (ADR-018 D8).
@EnableScheduling
public class CrmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmServiceApplication.class, args);
    }
}
