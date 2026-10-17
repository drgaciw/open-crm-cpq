package com.opencrm.contract.service;

import com.opencrm.contract.controller.RenewalPolicyController;
import com.opencrm.contract.repository.documents.RenewalPolicyRecordRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Loads the renewal-policy beans the way component scan does, without a database.
 * A stereotype with more than one constructor and no autowire marker fails here, in the unit gate.
 */
@Tag("unit")
class RenewalPolicyWiringContextTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NewBeans.class);

    @Test
    void newBeansLoadWithTheConstructorsSpringUses() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded).hasSingleBean(JpaRenewalPolicyRecords.class);
            assertThat(loaded).hasSingleBean(RenewalPolicyRecordService.class);
            assertThat(loaded).hasSingleBean(RenewalPolicyController.class);
        });
    }

    /**
     * The repository is a Spring Data interface. This supplies a stand-in so the adapter can be constructed.
     * The service and controller are the real classes.
     */
    @Configuration(proxyBeanMethods = false)
    @Import({JpaRenewalPolicyRecords.class, RenewalPolicyRecordService.class, RenewalPolicyController.class})
    static class NewBeans {
        @Bean
        RenewalPolicyRecordRepository renewalPolicyRecordRepository() {
            return mock(RenewalPolicyRecordRepository.class);
        }
    }
}
