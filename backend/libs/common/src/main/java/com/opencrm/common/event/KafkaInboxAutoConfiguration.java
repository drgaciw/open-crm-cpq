package com.opencrm.common.event;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;

/**
 * Registers {@link KafkaInbox} for services whose schema has a {@code processed_events} table (ADR-018 D6), plus a
 * daily purge of claims older than {@code opencrm.kafka.inbox.retention} (default 30 days, well past topic retention
 * and any relay re-send). Opt-in with {@code opencrm.kafka.inbox.enabled=true}.
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnProperty(name = "opencrm.kafka.inbox.enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaInboxAutoConfiguration.InboxProperties.class)
public class KafkaInboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaInbox kafkaInbox(JdbcTemplate jdbcTemplate) {
        return new KafkaInbox(jdbcTemplate);
    }

    @Bean
    public KafkaInboxPurge kafkaInboxPurge(KafkaInbox inbox, InboxProperties properties) {
        return new KafkaInboxPurge(inbox, properties.getRetention(), Clock.systemUTC());
    }

    /** {@code opencrm.kafka.inbox.*}. */
    @Getter
    @Setter
    @ConfigurationProperties(prefix = "opencrm.kafka.inbox")
    public static class InboxProperties {
        private boolean enabled;
        /** Claims older than this are purged daily. */
        private Duration retention = Duration.ofDays(30);
    }

    /** Daily purge of old inbox claims. Idempotent, so every replica may run it. */
    @Slf4j
    public static class KafkaInboxPurge {

        private final KafkaInbox inbox;
        private final Duration retention;
        private final Clock clock;

        public KafkaInboxPurge(KafkaInbox inbox, Duration retention, Clock clock) {
            this.inbox = inbox;
            this.retention = retention;
            this.clock = clock;
        }

        @Scheduled(cron = "${opencrm.kafka.inbox.purge-cron:0 30 3 * * *}")
        public void purge() {
            int removed = inbox.purgeProcessedBefore(clock.instant().minus(retention));
            if (removed > 0) {
                log.info("Purged {} inbox claims older than {}", removed, retention);
            }
        }
    }
}
