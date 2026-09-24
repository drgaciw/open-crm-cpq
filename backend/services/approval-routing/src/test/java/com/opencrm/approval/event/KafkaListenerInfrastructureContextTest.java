package com.opencrm.approval.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.approval.event.shadow.DiscountTierShadowService;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import com.opencrm.security.CurrentActor;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

/**
 * ADR-018 D1 regression guard: approval-routing's Kafka listeners must be able to start.
 *
 * <p>Verified defect (2026-09-22, HEAD {@code 4a29d31}): the service declares {@code @EnableKafka} and no
 * listener container factory of its own, and it depended on plain {@code spring-kafka}. On Spring Boot 4 the
 * {@code kafkaListenerContainerFactory} comes from {@code spring-boot-kafka} auto-configuration, so startup failed
 * with {@code NoSuchBeanDefinitionException: No bean named 'kafkaListenerContainerFactory'}. The fix is
 * {@code spring-boot-starter-kafka}.
 *
 * <p>The context mirrors the service's Kafka wiring: {@code @EnableKafka}, the real consumer bean(s) and Boot's
 * Kafka auto-configuration. Containers are not auto-started, so no broker is needed.
 */
@DisplayName("ADR-018 D1: approval-routing Kafka listener infrastructure")
class KafkaListenerInfrastructureContextTest {

    @Test
    @DisplayName("Boot discovers its Kafka auto-configuration on this service's classpath")
    void bootDiscoversKafkaAutoConfiguration() {
        List<String> candidates = ImportCandidates
                .load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();

        assertThat(candidates).contains(KafkaAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("Listener container factory exists and the consumer is subscribed to its topic(s)")
    void consumersStartWithServiceKafkaWiring() {
        new ApplicationContextRunner()
                .withUserConfiguration(ServiceStyleKafkaWiring.class)
                .withBean(ApprovalRequestRepository.class, () -> mock(ApprovalRequestRepository.class))
                .withBean(DiscountTierShadowService.class, () -> mock(DiscountTierShadowService.class))
                .withBean(KafkaInbox.class, () -> mock(KafkaInbox.class))
                .withBean(CurrentActor.class, () -> new CurrentActor("service:approval-routing", false))
                .withBean(DiscountApprovalEventConsumer.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "spring.kafka.listener.auto-startup=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("kafkaListenerContainerFactory");
                    List<String> topics = context.getBean(KafkaListenerEndpointRegistry.class)
                            .getListenerContainers().stream()
                            .flatMap(container -> Arrays.stream(container.getContainerProperties().getTopics()))
                            .toList();
                    assertThat(topics).contains(KafkaTopics.DISCOUNT_APPROVAL_REQUIRED);
                });
    }

    /** Mirrors the application class's Kafka-relevant annotation. */
    @Configuration(proxyBeanMethods = false)
    @EnableKafka
    static class ServiceStyleKafkaWiring {
    }
}
