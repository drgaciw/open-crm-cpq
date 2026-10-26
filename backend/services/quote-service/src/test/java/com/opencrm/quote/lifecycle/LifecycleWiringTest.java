package com.opencrm.quote.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.SubscriptionScheduleClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Docker-free wiring. One assertion per new lifecycle bean. No Testcontainer and no full application.
 */
@DisplayName("LifecycleChange bean wiring")
class LifecycleWiringTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Scan.class)
            .withBean(LifecycleChangeRepository.class, () -> mock(LifecycleChangeRepository.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(PricingServiceClient.class, () -> mock(PricingServiceClient.class))
            .withBean(SubscriptionScheduleClient.class, () -> mock(SubscriptionScheduleClient.class))
            .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());

    @Test
    @DisplayName("controller, service, audit writer and subscription reader each load once")
    void newBeansLoad() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded).hasSingleBean(LifecycleChangeController.class);
            assertThat(loaded).hasSingleBean(LifecycleChangeService.class);
            assertThat(loaded).hasSingleBean(JdbcLifecycleChangeAuditWriter.class);
            assertThat(loaded).hasSingleBean(LifecycleChangeAuditWriter.class);
            assertThat(loaded).hasSingleBean(ScheduleSubscriptionTargetReader.class);
            assertThat(loaded).hasSingleBean(SubscriptionTargetReader.class);
        });
    }

    @Configuration
    @ComponentScan(basePackageClasses = LifecycleChangeService.class)
    static class Scan {
    }
}
