package com.opencrm.pricing.cost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("unit")
@DisplayName("cost basis wiring: the JPA adapter replaces the fail-closed fallback; properties bind")
class CostBasisWiringTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(CostBasisConfiguration.class);

    @Test
    @DisplayName("without the adapter the fallback answers empty for everything")
    void fallbackWithoutAdapter() {
        context.run(application -> assertThat(application.getBean(CostBasisLookup.class)
                .find("PROD-SEAT", LocalDate.of(2026, 10, 1))).isEmpty());
    }

    @Test
    @DisplayName("with the adapter present it is the only CostBasisLookup")
    void adapterReplacesFallback() {
        context.withBean(CostBasisRowRepository.class, () -> mock(CostBasisRowRepository.class))
                .withBean(JpaCostBasisLookup.class)
                .run(application -> {
                    assertThat(application).hasSingleBean(CostBasisLookup.class);
                    assertThat(application.getBean(CostBasisLookup.class)).isInstanceOf(JpaCostBasisLookup.class);
                });
    }

    @Test
    @DisplayName("opencrm.pricing.cost-basis.max-rows defaults to 10000, binds, and must be positive")
    void properties() {
        ApplicationContextRunner properties = new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class);
        properties.run(application -> assertThat(application.getBean(CostBasisProperties.class).getMaxRows())
                .isEqualTo(10_000));
        properties.withPropertyValues("opencrm.pricing.cost-basis.max-rows=50")
                .run(application -> assertThat(application.getBean(CostBasisProperties.class).getMaxRows())
                        .isEqualTo(50));
        properties.withPropertyValues("opencrm.pricing.cost-basis.max-rows=0")
                .run(application -> assertThat(application).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CostBasisProperties.class)
    static class PropertiesConfiguration {
    }
}
