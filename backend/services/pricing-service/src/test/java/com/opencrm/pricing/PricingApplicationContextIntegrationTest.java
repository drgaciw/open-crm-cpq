package com.opencrm.pricing;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.execution.margin.CostBasisMarginEvaluator;
import com.opencrm.pricing.execution.margin.MarginProperties;
import com.opencrm.pricing.execution.margin.MarginSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review finding M6, full context: pricing-service starts as {@link PricingServiceApplication} assembles it, on
 * PostgreSQL (the {@code test} profile's Testcontainers JDBC URL and the real Flyway chain), in both margin modes.
 * The scanned {@code @Component} cost basis adapter replaces the root fallback, so exactly one
 * {@link CostBasisLookup} exists and the evaluator is wired to it; {@link MarginProperties} binds. Needs Docker; the
 * {@code unit-tests} profile excludes it. The Docker-free wiring proof is {@code PricingMarginContextTest}.
 */
@Tag("integration")
@DisplayName("M6: pricing-service application context in both margin modes")
class PricingApplicationContextIntegrationTest {

    private static final String FALLBACK_BEAN = "emptyCostBasisLookup";

    static void assertMarginWiring(ApplicationContext context, MarginSource expected) {
        assertThat(context.getBean(MarginProperties.class).getSource()).isEqualTo(expected);
        assertThat(context.getBeansOfType(CostBasisLookup.class)).hasSize(1);
        assertThat(context.containsBean(FALLBACK_BEAN)).isFalse();
        CostBasisLookup lookup = context.getBean(CostBasisLookup.class);
        assertThat(AnnotatedElementUtils.hasAnnotation(AopUtils.getTargetClass(lookup), Component.class)).isTrue();
        assertThat(context.getBeansOfType(CostBasisMarginEvaluator.class)).hasSize(1);
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @DisplayName("opencrm.pricing.margin.source unset (placeholder)")
    class PlaceholderMode {
        @Autowired
        ApplicationContext context;

        @Test
        void startsWithOneCostBasisLookup() {
            assertMarginWiring(context, MarginSource.PLACEHOLDER);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = "opencrm.pricing.margin.source=cost-basis")
    @DisplayName("opencrm.pricing.margin.source=cost-basis")
    class CostBasisMode {
        @Autowired
        ApplicationContext context;

        @Test
        void startsWithOneCostBasisLookup() {
            assertMarginWiring(context, MarginSource.COST_BASIS);
        }
    }
}
