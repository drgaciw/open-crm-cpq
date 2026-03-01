package com.opencrm.common.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Money value object - immutable representation of monetary amounts.
 * Uses BigDecimal with HALF_UP rounding to avoid floating-point precision issues.
 * Constitutional requirement: NO primitive float/double for financial calculations.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@ToString
public class Money implements Serializable, Comparable<Money> {
    
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
    private static final int DEFAULT_SCALE = 2;
    
    private final BigDecimal amount;
    private final String currencyCode;
    
    protected Money() {
        // JPA requires no-arg constructor
        this.amount = BigDecimal.ZERO;
        this.currencyCode = "USD";
    }
    
    @JsonCreator
    public Money(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currencyCode") String currencyCode) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code cannot be null or blank");
        }
        
        // Validate currency code
        Currency.getInstance(currencyCode);
        
        this.amount = amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        this.currencyCode = currencyCode;
    }
    
    public Money(BigDecimal amount) {
        this(amount, "USD");
    }
    
    public Money(double amount, String currencyCode) {
        this(BigDecimal.valueOf(amount), currencyCode);
    }
    
    public Money(double amount) {
        this(BigDecimal.valueOf(amount), "USD");
    }
    
    // Factory methods
    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, currencyCode);
    }
    
    public static Money zero() {
        return zero("USD");
    }
    
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }
    
    public static Money of(double amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), currencyCode);
    }
    
    public static Money usd(BigDecimal amount) {
        return new Money(amount, "USD");
    }
    
    public static Money usd(double amount) {
        return new Money(BigDecimal.valueOf(amount), "USD");
    }
    
    // Arithmetic operations
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currencyCode);
    }
    
    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currencyCode);
    }
    
    public Money multiply(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier), this.currencyCode);
    }
    
    public Money multiply(double multiplier) {
        return multiply(BigDecimal.valueOf(multiplier));
    }
    
    public Money divide(BigDecimal divisor) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return new Money(this.amount.divide(divisor, DEFAULT_SCALE, DEFAULT_ROUNDING), this.currencyCode);
    }
    
    public Money divide(double divisor) {
        return divide(BigDecimal.valueOf(divisor));
    }
    
    public Money negate() {
        return new Money(this.amount.negate(), this.currencyCode);
    }
    
    public Money abs() {
        return new Money(this.amount.abs(), this.currencyCode);
    }
    
    // Percentage operations
    public Money percentage(BigDecimal percentage) {
        return multiply(percentage.divide(BigDecimal.valueOf(100), DEFAULT_SCALE, DEFAULT_ROUNDING));
    }
    
    public Money percentage(double percentage) {
        return percentage(BigDecimal.valueOf(percentage));
    }
    
    // Comparison operations
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }
    
    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }
    
    public boolean isGreaterThanOrEqual(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) >= 0;
    }
    
    public boolean isLessThanOrEqual(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) <= 0;
    }
    
    @Override
    public int compareTo(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount);
    }
    
    // Utility methods
    public Currency getCurrency() {
        return Currency.getInstance(currencyCode);
    }
    
    private void assertSameCurrency(Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException(
                    String.format("Cannot operate on different currencies: %s and %s",
                            this.currencyCode, other.currencyCode));
        }
    }
    
    public String toFormattedString() {
        return String.format("%s %s", currencyCode, amount.toPlainString());
    }
}
