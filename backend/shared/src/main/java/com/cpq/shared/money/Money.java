package com.cpq.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import jakarta.validation.constraints.NotNull;

/**
 * Immutable value object for financial calculations.
 * Uses BigDecimal with HALF_UP rounding for all operations.
 */
public final class Money {
    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    @NotNull
    private final BigDecimal amount;
    @NotNull
    private final String currency;
    
    public Money(BigDecimal amount, String currency) {
        this.amount = amount.setScale(DEFAULT_SCALE, ROUNDING_MODE);
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
    }
    
    public Money(long amount, String currency) {
        this(BigDecimal.valueOf(amount), currency);
    }
    
    public Money(double amount, String currency) {
        this(BigDecimal.valueOf(amount), currency);
    }
    
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public Money add(Money other) {
        validateCurrency(other);
        return new Money(this.amount.add(other.amount), currency);
    }
    
    public Money subtract(Money other) {
        validateCurrency(other);
        return new Money(this.amount.subtract(other.amount), currency);
    }
    
    public Money multiply(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier).setScale(DEFAULT_SCALE, ROUNDING_MODE), currency);
    }
    
    public Money multiply(double multiplier) {
        return multiply(BigDecimal.valueOf(multiplier));
    }
    
    public Money divide(BigDecimal divisor) {
        return new Money(this.amount.divide(divisor, DEFAULT_SCALE, ROUNDING_MODE), currency);
    }
    
    public Money divide(double divisor) {
        return divide(BigDecimal.valueOf(divisor));
    }
    
    public boolean isGreaterThan(Money other) {
        validateCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }
    
    public boolean isGreaterThanOrEqual(Money other) {
        validateCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }
    
    public boolean isLessThan(Money other) {
        validateCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }
    
    public boolean isLessThanOrEqual(Money other) {
        validateCurrency(other);
        return this.amount.compareTo(other.amount) <= 0;
    }
    
    public boolean isEqualTo(Money other) {
        validateCurrency(other);
        return this.amount.compareTo(other.amount) == 0;
    }
    
    private void validateCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot perform operation on different currencies: " + 
                this.currency + " vs " + other.currency);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount) && 
               Objects.equals(currency, money.currency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
    
    @Override
    public String toString() {
        return amount + " " + currency;
    }
}