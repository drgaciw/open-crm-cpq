package com.opencrm.ai.task;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Terminal queue state. Retrying work keeps a null outcome until it succeeds or parks.
 */
public enum Outcome {
    SUCCEEDED,
    PARKED;

    public String wire() {
        return switch (this) {
            case SUCCEEDED -> "succeeded";
            case PARKED -> "parked";
        };
    }

    public static Outcome fromWire(String value) {
        return switch (value) {
            case "succeeded" -> SUCCEEDED;
            case "parked" -> PARKED;
            default -> throw new IllegalArgumentException("Unknown outcome");
        };
    }

    @Converter
    public static final class WireConverter implements AttributeConverter<Outcome, String> {
        @Override
        public String convertToDatabaseColumn(Outcome attribute) {
            if (attribute == null) {
                return null;
            }
            return attribute.wire();
        }

        @Override
        public Outcome convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromWire(dbData);
        }
    }
}
