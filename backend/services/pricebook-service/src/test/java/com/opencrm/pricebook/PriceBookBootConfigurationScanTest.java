package com.opencrm.pricebook;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.AnnotatedClassFinder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for nightly Backend Integration run 38995521632: an implicit
 * {@code @SpringBootTest} lookup from this package threw
 * {@code Found multiple @SpringBootConfiguration} and failed the reactor before later modules ran.
 */
class PriceBookBootConfigurationScanTest {

    @Test
    void implicitLookupFindsOnlyThePriceBookServiceApplication() {
        assertThat(bootConfigurationFrom(PriceBookOutboxWiringTest.class))
                .isEqualTo(PriceBookServiceApplication.class);
    }

    private static Class<?> bootConfigurationFrom(Class<?> testClass) {
        try {
            Class<?> found = new AnnotatedClassFinder(SpringBootConfiguration.class).findFromClass(testClass);
            if (found == null) {
                throw new AssertionError("implicit @SpringBootTest lookup found no @SpringBootConfiguration");
            }
            return found;
        } catch (IllegalStateException ex) {
            throw new AssertionError(
                    "implicit @SpringBootTest lookup must resolve one @SpringBootConfiguration: " + ex.getMessage(),
                    ex);
        }
    }
}
