package com.opencrm.common.event;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("outbox auto-configuration (ADR-018 D1/D2/D10)")
class OutboxAutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class,
                    OutboxRelayAutoConfiguration.class,
                    OutboxObservabilityAutoConfiguration.class,
                    OutboxHealthAutoConfiguration.class,
                    OutboxMetricsAutoConfiguration.class))
            .withBean(OutboxEventRepository.class, () -> mock(OutboxEventRepository.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092");

    @Test
    @DisplayName("relay enabled: producer delivery settings are pinned (acks=all, idempotence, max.block.ms)")
    void producerDeliverySettingsArePinned() {
        context.withPropertyValues(
                        "opencrm.outbox.relay.enabled=true",
                        "opencrm.outbox.relay.ack-timeout=7s",
                        "spring.kafka.producer.acks=1")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OutboxRelayPublisher.class);
                    var configs = ctx.getBean(DefaultKafkaProducerFactory.class).getConfigurationProperties();
                    assertThat(configs)
                            .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                            .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                            .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 7000L);
                });
    }

    @Test
    @DisplayName("relay disabled: no relay and producer settings untouched, but health and metrics still register")
    void observabilityWithoutRelay() {
        context.withPropertyValues("spring.kafka.producer.acks=1").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(OutboxRelayPublisher.class);
            assertThat(ctx.getBean(DefaultKafkaProducerFactory.class).getConfigurationProperties())
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "1");
            assertThat(ctx).hasSingleBean(OutboxBacklogProbe.class);
            assertThat(ctx).hasSingleBean(OutboxMetrics.class);
            assertThat(ctx).hasBean("outboxHealthIndicator");
            assertThat(ctx.getBean(OutboxHealthIndicator.class).health().getStatus()).isEqualTo(Status.UNKNOWN);
        });
    }

    @Test
    @DisplayName("the relay reports to the metrics observer")
    void relayUsesMetricsObserver() {
        context.withPropertyValues("opencrm.outbox.relay.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(OutboxRelayObserver.class);
            assertThat(ctx.getBean(OutboxRelayObserver.class)).isInstanceOf(OutboxMetrics.class);
            assertThat(ctx.getBean(OutboxHealthIndicator.class).health().getStatus()).isEqualTo(Status.UP);
        });
    }

    @Test
    @DisplayName("health indicator can be switched off with management.health.outbox.enabled=false")
    void healthIndicatorCanBeDisabled() {
        context.withPropertyValues("management.health.outbox.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxHealthIndicator.class));
    }
}
