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
        Percentage pct = Percentage.of(25.0);
        assertEquals(new BigDecimal("0.2500"), pct.asDecimal());
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
        assertTrue(Percentage.of(-5).isNegative());
        assertTrue(Percentage.zero().isZero());
    }

    @Test
    @DisplayName("should correctly identify negative percentages")
    void isNegative() {
        assertTrue(Percentage.of(-5.0).isNegative());
        assertFalse(Percentage.of(5.0).isNegative());
        assertFalse(Percentage.zero().isNegative());
    }

    @Test
    @DisplayName("should compare percentages")
    void compare() {
        Percentage a = Percentage.of(20);
        Percentage b = Percentage.of(10);
        assertTrue(a.compareTo(b) > 0);
        assertTrue(b.compareTo(a) < 0);
    }

    @Test
    @DisplayName("should reject null value")
    void rejectNull() {
        assertThrows(IllegalArgumentException.class, () -> new Percentage((BigDecimal) null));
    }
}
