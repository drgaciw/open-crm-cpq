package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Quote line item fact for rules engine evaluation in pricing-service Drools rules.
 * Bindings: {@code lineItemType}, {@code quantity}, {@code listPrice}, {@code quote}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLineItemFact {

    private String externalId;
    private String lineItemType;
    private String productExternalId;
    private int quantity;
    private BigDecimal listPrice;
    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal finalPrice;
    private QuoteFact quote;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // Helper methods for metadata access
    public String getString(String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    public Integer getInt(String key) {
        Object value = metadata.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public BigDecimal getDecimal(String key) {
        Object value = metadata.get(key);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        } else if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public Boolean getBoolean(String key) {
        Object value = metadata.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    public void setString(String key, String value) {
        metadata.put(key, value);
    }

    public void setInt(String key, Integer value) {
        metadata.put(key, value);
    }

    public void setDecimal(String key, BigDecimal value) {
        metadata.put(key, value);
    }

    public void setBoolean(String key, Boolean value) {
        metadata.put(key, value);
    }
}
