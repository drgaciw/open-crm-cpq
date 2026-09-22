package com.opencrm.quote.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.OutboxEventRepository;
import com.opencrm.common.event.OutboxRelayAutoConfiguration;
import com.opencrm.common.event.OutboxRelayPublisher;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * ADR-018 D1 regression guard: the transactional-outbox relay must be registered in quote-service.
 *
 * <p>Verified defect (2026-09-22, HEAD {@code 4a29d31}), with two layers:
 * <ol>
 *   <li>quote-service depended on plain {@code spring-kafka}. On Spring Boot 4, {@code KafkaAutoConfiguration}
 *       lives in {@code spring-boot-kafka}, so no {@code KafkaTemplate} existed.</li>
 *   <li>The relay was a component-scanned {@code @Component} guarded by
 *       {@code @ConditionalOnBean(KafkaTemplate.class)}. That condition is evaluated before auto-configured beans
 *       exist, so the relay was skipped even when a {@code KafkaTemplate} was present.</li>
 * </ol>
 * Fix: {@code spring-boot-starter-kafka}, plus {@link OutboxRelayAutoConfiguration} ordered after Boot's Kafka
 * auto-configuration. The relay is opt-in ({@code opencrm.outbox.relay.enabled=true}) because enabling it replays
 * the accumulated backlog. These tests mirror the service's wiring: {@code @SpringBootApplication} scans
 * {@code com.opencrm.common} with the auto-configuration exclude filter, and Boot then applies the discovered
 * auto-configurations.
 */
@DisplayName("ADR-018 D1: outbox relay registration")
class OutboxRelayRegistrationContextTest {

    private final ApplicationContextRunner serviceStyleContext = new ApplicationContextRunner()
            .withUserConfiguration(ServiceStyleComponentScan.class)
            .withBean(OutboxEventRepository.class, () -> mock(OutboxEventRepository.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092");

    @Test
    @DisplayName("Boot discovers its Kafka auto-configuration and the outbox relay auto-configuration")
    void bootDiscoversKafkaAndOutboxRelayAutoConfigurations() {
        List<String> candidates = ImportCandidates
                .load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();

        assertThat(candidates)
                .as("auto-configuration candidates Boot will consider for quote-service")
                .contains(KafkaAutoConfiguration.class.getName(), OutboxRelayAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("Enabled: relay is registered exactly once with the service's scan and Boot's Kafka auto-configuration")
    void relayRegistersOnceWithServiceScanAndBootAutoConfiguration() {
        serviceStyleContext
                .withPropertyValues(OutboxRelayAutoConfiguration.ENABLED_PROPERTY + "=true")
                .withConfiguration(AutoConfigurations.of(
                        KafkaAutoConfiguration.class, OutboxRelayAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KafkaTemplate.class);
                    assertThat(context).hasSingleBean(OutboxEventPublisher.class);
                    assertThat(context)
                            .as("the relay must exist, or outbox rows are written and never published")
                            .hasSingleBean(OutboxRelayPublisher.class);
                });
    }

    @Test
    @DisplayName("Off by default: no relay unless opencrm.outbox.relay.enabled=true (backlog replay is opt-in)")
    void relayIsOffByDefault() {
        serviceStyleContext
                .withConfiguration(AutoConfigurations.of(
                        KafkaAutoConfiguration.class, OutboxRelayAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KafkaTemplate.class);
                    assertThat(context).doesNotHaveBean(OutboxRelayPublisher.class);
                });
    }

    @Test
    @DisplayName("Enabled but no KafkaTemplate: no relay, and startup does not fail")
    void relayIsSkippedWithoutKafkaTemplate() {
        serviceStyleContext
                .withPropertyValues(OutboxRelayAutoConfiguration.ENABLED_PROPERTY + "=true")
                .withConfiguration(AutoConfigurations.of(OutboxRelayAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OutboxRelayPublisher.class);
                });
    }

    /**
     * Mirrors quote-service's {@code @SpringBootApplication(scanBasePackages = {..., "com.opencrm.common", ...})}:
     * default component scanning with Boot's auto-configuration exclude filter.
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = OutboxRelayPublisher.class,
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class))
    static class ServiceStyleComponentScan {
    }
}
