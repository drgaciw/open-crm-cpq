package com.opencrm.pricing.execution.margin;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.margincontextstub.StubCostBasisLookup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review finding M6, Docker-free half: the margin wiring as pricing-service assembles it, minus the database. The
 * root fallback is the only {@link CostBasisLookup} when no adapter is present, in both margin modes; a scanned
 * {@code @Component} adapter replaces it; {@link MarginProperties} binds. The full {@code @SpringBootTest} on
 * PostgreSQL is {@code PricingApplicationContextIntegrationTest}.
 */
@Tag("unit")
@DisplayName("M6: pricing margin context wiring (fallback, component replacement, properties)")
class PricingMarginContextTest {

    private static final String FALLBACK_BEAN = "emptyCostBasisLookup";

    private static Class<?> fallbackConfiguration() throws ClassNotFoundException {
        // Package-private in the root-owned cost package.
        return Class.forName("com.opencrm.pricing.cost.CostBasisConfiguration");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"placeholder", "cost-basis"})
    @DisplayName("with no adapter the root fallback is the only CostBasisLookup, and it is empty")
    void onlyFallback(String mode) throws Exception {
        new ApplicationContextRunner()
                .withPropertyValues("opencrm.pricing.margin.source=" + mode)
                .withUserConfiguration(fallbackConfiguration(), BindMargin.class, CostBasisMarginEvaluator.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CostBasisLookup.class);
                    assertThat(context.getBeanNamesForType(CostBasisLookup.class)).containsExactly(FALLBACK_BEAN);
                    assertThat(context.getBean(CostBasisLookup.class)
                            .find("PROD-SEAT", LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15))).isEmpty();
                    assertThat(context).hasSingleBean(CostBasisMarginEvaluator.class);
                    assertThat(context.getBean(MarginProperties.class).getSource()).isEqualTo(MarginSource.from(mode));
                });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"placeholder", "cost-basis"})
    @DisplayName("a component-scanned adapter replaces the fallback, and the evaluator receives it")
    void componentReplacesFallback(String mode) throws Exception {
        new ApplicationContextRunner()
                .withPropertyValues("opencrm.pricing.margin.source=" + mode)
                .withUserConfiguration(fallbackConfiguration(), ScanStub.class, BindMargin.class,
                        CostBasisMarginEvaluator.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CostBasisLookup.class);
                    assertThat(context.getBean(CostBasisLookup.class)).isInstanceOf(StubCostBasisLookup.class);
                    assertThat(context.containsBean(FALLBACK_BEAN)).isFalse();
                    assertThat(context).hasSingleBean(CostBasisMarginEvaluator.class);
                });
    }

    @Test
    @DisplayName("opencrm.pricing.margin.source defaults to placeholder, binds both spellings, rejects unknown values")
    void marginPropertiesBind() {
        ApplicationContextRunner properties = new ApplicationContextRunner().withUserConfiguration(BindMargin.class);
        properties.run(context -> assertThat(context.getBean(MarginProperties.class).getSource())
                .isEqualTo(MarginSource.PLACEHOLDER));
        properties.withPropertyValues("opencrm.pricing.margin.source=cost-basis")
                .run(context -> assertThat(context.getBean(MarginProperties.class).getSource())
                        .isEqualTo(MarginSource.COST_BASIS));
        properties.withPropertyValues("opencrm.pricing.margin.source=COST_BASIS")
                .run(context -> assertThat(context.getBean(MarginProperties.class).getSource())
                        .isEqualTo(MarginSource.COST_BASIS));
        properties.withPropertyValues("opencrm.pricing.margin.source=bogus")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MarginProperties.class)
    static class BindMargin {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = StubCostBasisLookup.class)
    static class ScanStub {
    }
}
