package com.opencrm.rules.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Shared metadata access contract for pricing facts used by DRL rules.
 *
 * <p>Rule facts keep first-class fields for stable pricing inputs and use metadata for
 * catalog-specific or transitional values that are not yet part of the canonical model.
 */
public interface MetadataBackedFact {

    Map<String, Object> getMetadata();

    default String getString(String key) {
        Object value = getMetadata().get(key);
        return value != null ? value.toString() : null;
    }

    default Integer getInt(String key) {
        Object value = getMetadata().get(key);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    default BigDecimal getDecimal(String key) {
        Object value = getMetadata().get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String string) {
            try {
                return new BigDecimal(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    default Boolean getBoolean(String key) {
        Object value = getMetadata().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return null;
    }

    default void setString(String key, String value) {
        getMetadata().put(key, value);
    }

    default void setInt(String key, Integer value) {
        getMetadata().put(key, value);
    }

    default void setDecimal(String key, BigDecimal value) {
        getMetadata().put(key, value);
    }

    default void setBoolean(String key, Boolean value) {
        getMetadata().put(key, value);
    }
}
