package com.opencrm.crm.recommendation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecommendedProductDto")
class RecommendedProductDtoTest {

    @Test
    @DisplayName("accepts confidence at both ends of the closed unit interval")
    void acceptsZeroAndOne() {
        assertThatCode(() -> new RecommendedProductDto("addon-analytics", "Because it fits.", BigDecimal.ZERO))
                .doesNotThrowAnyException();
        assertThatCode(() -> new RecommendedProductDto("addon-analytics", "Because it fits.", BigDecimal.ONE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a blank rationale and a confidence outside 0 to 1")
    void rejectsBlankRationaleAndOutOfRangeConfidence() {
        assertThatThrownBy(() -> new RecommendedProductDto("addon-analytics", "  ", new BigDecimal("0.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecommendedProductDto(" ", "Because it fits.", new BigDecimal("0.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecommendedProductDto("addon-analytics", "Because it fits.", new BigDecimal("1.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecommendedProductDto("addon-analytics", "Because it fits.", new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
