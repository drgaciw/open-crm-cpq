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

/**
 * Percentage value object - immutable representation of percentage values.
 * Used for discounts, margins, tax rates, etc.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@ToString
public class Percentage implements Serializable, Comparable<Percentage> {
    
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
    private static final int DEFAULT_SCALE = 4;
    
    private final BigDecimal value;
    
    protected Percentage() {
        this.value = BigDecimal.ZERO;
    }
    
    @JsonCreator
    public Percentage(@JsonProperty("value") BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Percentage value cannot be null");
        }
        this.value = value.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }
    
    public Percentage(double value) {
        this(BigDecimal.valueOf(value));
    }
    
    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }
    
    public static Percentage of(double value) {
        return new Percentage(BigDecimal.valueOf(value));
    }
    
    public static Percentage zero() {
        return new Percentage(BigDecimal.ZERO);
    }
    
    public BigDecimal asDecimal() {
        return value.divide(BigDecimal.valueOf(100), DEFAULT_SCALE, DEFAULT_ROUNDING);
    }
    
    public Percentage add(Percentage other) {
        return new Percentage(this.value.add(other.value));
    }
    
    public Percentage subtract(Percentage other) {
        return new Percentage(this.value.subtract(other.value));
    }
    
    public boolean isPositive() {
        return value.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isNegative() {
        return value.compareTo(BigDecimal.ZERO) < 0;
    }
    
    public boolean isZero() {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }
    
    @Override
    public int compareTo(Percentage other) {
        return value.compareTo(other.value);
    }
}
