package com.opencrm.pricing.renewal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the renewal-resolve beans the way component scan does, without a server.
 * A stereotype with more than one constructor and no autowire marker fails here, in the unit gate.
 */
@Tag("unit")
class RenewalResolveWiringContextTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NewBeans.class);

    @Test
    void newBeansLoadWithTheConstructorsSpringUses() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded).hasSingleBean(RenewalResolveService.class);
            assertThat(loaded).hasSingleBean(RenewalResolveController.class);
            assertThat(loaded).hasSingleBean(RenewalResolveRequestConverter.class);
            assertThat(loaded).hasSingleBean(RenewalResolveBodyLimitFilter.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            RenewalResolveService.class,
            RenewalResolveController.class,
            RenewalResolveRequestConverter.class,
            RenewalResolveBodyLimitFilter.class
    })
    static class NewBeans {
    }
}
