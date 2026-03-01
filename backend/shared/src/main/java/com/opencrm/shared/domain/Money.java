package com.opencrm.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(force = true)
public class Money implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final int SCALE = 2;

    @Column(name = "amount", precision = 19, scale = 2)
    private final BigDecimal amount;

    @Column(name = "currency_code", length = 3)
    private final String currencyCode;

    public Money(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currencyCode == null || currencyCode.length() != 3) {
            throw new IllegalArgumentException("Currency code must be a valid 3-letter ISO code");
        }
        this.amount = amount.setScale(SCALE, ROUNDING_MODE);
        this.currencyCode = currencyCode.toUpperCase();
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, currencyCode);
    }

    public Money add(Money other) {
        checkCurrency(other);
        return new Money(this.amount.add(other.amount), this.currencyCode);
    }

    public Money subtract(Money other) {
        checkCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currencyCode);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currencyCode);
    }
    
    public Money divide(BigDecimal divisor) {
        return new Money(this.amount.divide(divisor, SCALE, ROUNDING_MODE), this.currencyCode);
    }

    private void checkCurrency(Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Currency mismatch: " + this.currencyCode + " vs " + other.currencyCode);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount) && Objects.equals(currencyCode, money.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currencyCode);
    }

    @Override
    public String toString() {
        return String.format("%s %s", currencyCode, amount);
    }
}
