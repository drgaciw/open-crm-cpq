package com.opencrm.cpq.shared.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    @DisplayName("Money.of creates with correct amount and currency")
    void testCreation() {
        Money m = Money.of(new BigDecimal("99.99"), "USD");
        assertEquals(new BigDecimal("99.99"), m.getAmount());
        assertEquals("USD", m.getCurrencyCode());
    }

    @Test
    @DisplayName("Money uses HALF_UP rounding")
    void testRounding() {
        Money m = Money.of(new BigDecimal("10.005"), "USD");
        assertEquals(new BigDecimal("10.01"), m.getAmount());
    }

    @Test
    @DisplayName("Add two Money values")
    void testAdd() {
        Money a = Money.usd("100.00");
        Money b = Money.usd("50.50");
        Money result = a.add(b);
        assertEquals(Money.usd("150.50"), result);
    }

    @Test
    @DisplayName("Subtract Money values")
    void testSubtract() {
        Money a = Money.usd("100.00");
        Money b = Money.usd("30.25");
        assertEquals(Money.usd("69.75"), a.subtract(b));
    }

    @Test
    @DisplayName("Multiply by BigDecimal")
    void testMultiply() {
        Money m = Money.usd("10.00");
        Money result = m.multiply(new BigDecimal("3"));
        assertEquals(Money.usd("30.00"), result);
    }

    @Test
    @DisplayName("Divide with HALF_UP rounding")
    void testDivide() {
        Money m = Money.usd("10.00");
        Money result = m.divide(new BigDecimal("3"));
        assertEquals(Money.usd("3.33"), result);
    }

    @Test
    @DisplayName("Currency mismatch throws IllegalArgumentException")
    void testCurrencyMismatch() {
        Money usd = Money.usd("10.00");
        Money eur = Money.of("10.00", "EUR");
        assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
    }

    @Test
    @DisplayName("percentOff applies correct discount")
    void testPercentOff() {
        Money m = Money.usd("100.00");
        Money result = m.percentOff(new BigDecimal("15"));
        assertEquals(Money.usd("85.00"), result);
    }

    @Test
    @DisplayName("Zero factory method")
    void testZero() {
        Money zero = Money.zero("USD");
        assertTrue(zero.isZero());
        assertFalse(zero.isPositive());
        assertFalse(zero.isNegative());
    }

    @Test
    @DisplayName("Comparison methods")
    void testComparisons() {
        Money a = Money.usd("100.00");
        Money b = Money.usd("50.00");
        assertTrue(a.isGreaterThan(b));
        assertTrue(b.isLessThan(a));
        assertFalse(a.isZero());
        assertTrue(a.isPositive());
    }

    @Test
    @DisplayName("Negate returns negative amount")
    void testNegate() {
        Money m = Money.usd("100.00");
        Money neg = m.negate();
        assertTrue(neg.isNegative());
        assertEquals(Money.usd("-100.00"), neg);
    }

    @Test
    @DisplayName("Division by zero throws ArithmeticException")
    void testDivisionByZero() {
        Money m = Money.usd("10.00");
        assertThrows(ArithmeticException.class, () -> m.divide(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Equals and hashCode for same values")
    void testEqualsHashCode() {
        Money a = Money.usd("100.00");
        Money b = Money.of(new BigDecimal("100.00"), Currency.getInstance("USD"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("toString format")
    void testToString() {
        Money m = Money.usd("42.50");
        assertEquals("USD 42.50", m.toString());
    }
}
