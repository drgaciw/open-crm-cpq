package com.opencrm.common.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for Money value object.
 * Validates BigDecimal + HALF_UP rounding, no float/double precision issues.
 */
@DisplayName("Money Value Object")
class MoneyTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create Money with BigDecimal and currency")
        void createWithBigDecimalAndCurrency() {
            Money money = Money.of(BigDecimal.valueOf(100.50), "USD");
            assertEquals(new BigDecimal("100.50"), money.getAmount());
            assertEquals("USD", money.getCurrencyCode());
        }

        @Test
        @DisplayName("should apply HALF_UP rounding on construction")
        void halfUpRoundingOnConstruction() {
            Money money = new Money(new BigDecimal("10.555"), "USD");
            assertEquals(new BigDecimal("10.56"), money.getAmount());
        }

        @Test
        @DisplayName("should reject null amount")
        void rejectNullAmount() {
            assertThrows(IllegalArgumentException.class, () -> new Money(null, "USD"));
        }

        @Test
        @DisplayName("should reject invalid currency code")
        void rejectInvalidCurrency() {
            assertThrows(IllegalArgumentException.class, () -> Money.of(BigDecimal.TEN, "XYZ"));
        }

        @Test
        @DisplayName("should create zero Money")
        void createZero() {
            Money zero = Money.zero("EUR");
            assertTrue(zero.isZero());
            assertEquals("EUR", zero.getCurrencyCode());
        }
    }

    @Nested
    @DisplayName("Arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("should add two Money values")
        void add() {
            Money a = Money.usd(BigDecimal.valueOf(10.50));
            Money b = Money.usd(BigDecimal.valueOf(20.30));
            Money result = a.add(b);
            assertEquals(new BigDecimal("30.80"), result.getAmount());
        }

        @Test
        @DisplayName("should subtract two Money values")
        void subtract() {
            // Positive result
            Money a = Money.usd(BigDecimal.valueOf(50.00));
            Money b = Money.usd(BigDecimal.valueOf(20.50));
            Money result = a.subtract(b);
            assertEquals(new BigDecimal("29.50"), result.getAmount());

            // Zero result
            Money zeroResult = a.subtract(a);
            assertEquals(new BigDecimal("0.00"), zeroResult.getAmount());
            assertTrue(zeroResult.isZero());

            // Negative result
            Money negativeResult = b.subtract(a);
            assertEquals(new BigDecimal("-29.50"), negativeResult.getAmount());
            assertTrue(negativeResult.isNegative());
        }

        @Test
        @DisplayName("should multiply by quantity")
        void multiply() {
            Money price = Money.usd(BigDecimal.valueOf(25.00));
            Money total = price.multiply(4);
            assertEquals(new BigDecimal("100.00"), total.getAmount());
        }

        @Test
        @DisplayName("should divide with HALF_UP rounding")
        void divide() {
            Money total = Money.usd(BigDecimal.valueOf(100.00));
            Money perUnit = total.divide(3);
            assertEquals(new BigDecimal("33.33"), perUnit.getAmount());
        }

        @Test
        @DisplayName("should reject division by zero")
        void divideByZero() {
            Money money = Money.usd(BigDecimal.valueOf(100));
            assertThrows(ArithmeticException.class, () -> money.divide(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("should calculate percentage")
        void percentage() {
            Money base = Money.usd(BigDecimal.valueOf(200.00));
            Money discount = base.percentage(BigDecimal.valueOf(15));
            assertEquals(new BigDecimal("30.00"), discount.getAmount());
        }

        @Test
        @DisplayName("should negate")
        void negate() {
            Money positive = Money.usd(BigDecimal.valueOf(50.00));
            Money negative = positive.negate();
            assertTrue(negative.isNegative());
            assertEquals(new BigDecimal("-50.00"), negative.getAmount());
        }
    }

    @Nested
    @DisplayName("Currency Safety")
    class CurrencySafety {

        @Test
        @DisplayName("should reject addition of different currencies")
        void rejectDifferentCurrencyAdd() {
            Money usd = Money.usd(BigDecimal.TEN);
            Money eur = Money.of(BigDecimal.TEN, "EUR");
            assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
        }

        @Test
        @DisplayName("should reject subtraction of different currencies")
        void rejectDifferentCurrencySubtract() {
            Money usd = Money.usd(BigDecimal.TEN);
            Money eur = Money.of(BigDecimal.TEN, "EUR");
            assertThrows(IllegalArgumentException.class, () -> usd.subtract(eur));
        }

        @Test
        @DisplayName("should reject comparison of different currencies")
        void rejectDifferentCurrencyCompare() {
            Money usd = Money.usd(BigDecimal.TEN);
            Money eur = Money.of(BigDecimal.TEN, "EUR");
            assertThrows(IllegalArgumentException.class, () -> usd.isGreaterThan(eur));
        }
    }

    @Nested
    @DisplayName("Comparisons")
    class Comparisons {

        @Test
        @DisplayName("should detect positive, negative, zero")
        void signDetection() {
            assertTrue(Money.usd(BigDecimal.valueOf(10)).isPositive());
            assertTrue(Money.usd(BigDecimal.valueOf(-5)).isNegative());
            assertTrue(Money.zero("USD").isZero());
        }

        @Test
        @DisplayName("should compare greater/less than")
        void greaterLessThan() {
            Money a = Money.usd(BigDecimal.valueOf(100));
            Money b = Money.usd(BigDecimal.valueOf(50));
            assertTrue(a.isGreaterThan(b));
            assertTrue(b.isLessThan(a));
            assertTrue(a.isGreaterThanOrEqual(a));
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal for same amount and currency")
        void equalMoney() {
            Money a = Money.usd(BigDecimal.valueOf(100.00));
            Money b = Money.usd(new BigDecimal("100.00"));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different currencies")
        void differentCurrency() {
            Money usd = Money.usd(BigDecimal.valueOf(100));
            Money eur = Money.of(BigDecimal.valueOf(100), "EUR");
            assertNotEquals(usd, eur);
        }
    }
}
