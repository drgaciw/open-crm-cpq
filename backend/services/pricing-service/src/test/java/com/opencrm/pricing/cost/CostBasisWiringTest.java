package com.opencrm.pricing.cost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import org.springframework.util.unit.DataSize;

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

    @Test
    @DisplayName("m3: opencrm.pricing.cost-basis.max-bytes defaults to 5 MB, binds sizes like 64KB, and must be 1 B to 100 MB")
    void maxBytes() {
        ApplicationContextRunner properties = new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class);
        properties.run(application -> assertThat(application.getBean(CostBasisProperties.class).getMaxBytes())
                .isEqualTo(DataSize.ofMegabytes(5)));
        properties.withPropertyValues("opencrm.pricing.cost-basis.max-bytes=64KB")
                .run(application -> assertThat(application.getBean(CostBasisProperties.class).getMaxBytes())
                        .isEqualTo(DataSize.ofKilobytes(64)));
        properties.withPropertyValues("opencrm.pricing.cost-basis.max-bytes=2048")
                .run(application -> assertThat(application.getBean(CostBasisProperties.class).getMaxBytes())
                        .isEqualTo(DataSize.ofBytes(2048)));
        properties.withPropertyValues("opencrm.pricing.cost-basis.max-bytes=0")
                .run(application -> assertThat(application).hasFailed());
        properties.withPropertyValues("opencrm.pricing.cost-basis.max-bytes=101MB")
                .run(application -> assertThat(application).hasFailed());
    }

    @Test
    @DisplayName("B1: the adapter answers the 3-argument port; the 2-argument default delegates with equal dates")
    void adapterImplementsThreeArgumentPort() {
        CostBasisLookup lookup = new JpaCostBasisLookup(mock(CostBasisRowRepository.class));
        assertThat(lookup.find("PROD-SEAT", LocalDate.of(2028, 10, 15), LocalDate.of(2026, 10, 15))).isEmpty();
        assertThat(lookup.find("PROD-SEAT", LocalDate.of(2026, 10, 15))).isEmpty();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CostBasisProperties.class)
    static class PropertiesConfiguration {
    }
}
