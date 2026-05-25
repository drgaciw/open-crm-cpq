package com.opencrm.common.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for Percentage value object.
 */
@DisplayName("Percentage Value Object")
class PercentageTest {

    @Test
    @DisplayName("should create percentage with BigDecimal")
    void createWithBigDecimal() {
        Percentage pct = Percentage.of(BigDecimal.valueOf(15.5));
        assertEquals(new BigDecimal("15.5000"), pct.getValue());
    }

    @Test
    @DisplayName("should convert to decimal")
    void asDecimal() {
        // Happy path
        Percentage pct = Percentage.of(25.0);
        assertEquals(new BigDecimal("0.2500"), pct.asDecimal());

        // Edge cases
        assertEquals(new BigDecimal("1.0000"), Percentage.of(100.0).asDecimal());
        assertEquals(new BigDecimal("0.0000"), Percentage.of(0.0).asDecimal());

        // Negative values
        assertEquals(new BigDecimal("-0.1500"), Percentage.of(-15.0).asDecimal());
        assertEquals(new BigDecimal("-1.5000"), Percentage.of(-150.0).asDecimal());

        // Rounding cases (DEFAULT_SCALE = 4, DEFAULT_ROUNDING = HALF_UP)
        // 33.33333% -> 0.3333
        assertEquals(new BigDecimal("0.3333"), Percentage.of(33.3333).asDecimal());
        // 66.66666% -> 0.6667
        assertEquals(new BigDecimal("0.6667"), Percentage.of(66.6667).asDecimal());
    }

    @Test
    @DisplayName("should add percentages")
    void add() {
        Percentage a = Percentage.of(10.0);
        Percentage b = Percentage.of(5.0);
        Percentage result = a.add(b);
        assertEquals(new BigDecimal("15.0000"), result.getValue());
    }

    @Test
    @DisplayName("should subtract percentages")
    void subtract() {
        Percentage a = Percentage.of(20.0);
        Percentage b = Percentage.of(8.0);
        Percentage result = a.subtract(b);
        assertEquals(new BigDecimal("12.0000"), result.getValue());
    }

    @Test
    @DisplayName("should detect positive, negative, zero")
    void signDetection() {
        assertTrue(Percentage.of(10).isPositive());
        assertFalse(Percentage.of(-5).isPositive());
        assertFalse(Percentage.zero().isPositive());

        assertTrue(Percentage.of(-5).isNegative());
        assertFalse(Percentage.of(10).isNegative());
        assertFalse(Percentage.zero().isNegative());

        assertTrue(Percentage.zero().isZero());
        assertFalse(Percentage.of(10).isZero());
        assertFalse(Percentage.of(-5).isZero());
    }

    @Test
    @DisplayName("should compare percentages")
    void compare() {
        Percentage a = Percentage.of(20);
        Percentage b = Percentage.of(10);
        assertTrue(a.compareTo(b) > 0);
        assertTrue(b.compareTo(a) < 0);
        assertEquals(0, a.compareTo(Percentage.of(20)));
    }

    @Test
    @DisplayName("should reject null value")
    void rejectNull() {
        assertThrows(IllegalArgumentException.class, () -> new Percentage((BigDecimal) null));
    }
}
