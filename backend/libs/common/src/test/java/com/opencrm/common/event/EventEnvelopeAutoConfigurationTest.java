package com.opencrm.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.RecordInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-018 D4: envelope auto-configuration (actor and consumer correlation)")
class EventEnvelopeAutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class, EventCorrelationKafkaAutoConfiguration.class,
                    EventActorAutoConfiguration.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092", "spring.application.name=quote-service");

    @Test
    @DisplayName("without libs/security, events are attributed to the service")
    void defaultActorIsTheService() {
        context.run(ctx -> {
            assertThat(ctx).hasSingleBean(EventActorResolver.class);
            assertThat(ctx.getBean(EventActorResolver.class).currentActor()).isEqualTo("service:quote-service");
        });
    }

    @Test
    @DisplayName("a service's own EventActorResolver replaces the default")
    void ownResolverWins() {
        context.withBean(EventActorResolver.class, () -> () -> "service:custom")
                .run(ctx -> assertThat(ctx.getBean(EventActorResolver.class).currentActor()).isEqualTo("service:custom"));
    }

    @Test
    @DisplayName("Boot's listener containers run the correlation interceptor")
    void listenerContainersUseTheCorrelationInterceptor() {
        context.run(ctx -> {
            assertThat(ctx).hasSingleBean(EventCorrelationRecordInterceptor.class);
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    ctx.getBean(ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory.createContainer("cpq.quote.status.accepted").getRecordInterceptor())
                    .isSameAs(ctx.getBean(EventCorrelationRecordInterceptor.class));
        });
    }

    @Test
    @DisplayName("a service's own RecordInterceptor replaces the correlation interceptor")
    void ownInterceptorWins() {
        RecordInterceptor<Object, Object> own = (record, consumer) -> record;
        context.withBean("ownInterceptor", RecordInterceptor.class, () -> own)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(EventCorrelationRecordInterceptor.class));
    }
}
