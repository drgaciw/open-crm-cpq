package com.opencrm.jev.state;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The allow-list of features one use case may send to Jev. Anything not declared here cannot leave the
 * service (ADR-015 data minimisation).
 */
public record JevFeatureSchema(String name, int version, Map<String, FeatureType> features) {

    public enum FeatureType {
        /** A short code such as an enum constant or a band label. Never free text. */
        CODE,
        NUMBER,
        INTEGER,
        BOOLEAN
    }

    public JevFeatureSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("schema name must not be blank");
        }
        if (features == null || features.isEmpty()) {
            throw new IllegalArgumentException("schema " + name + " declares no features");
        }
        features = Collections.unmodifiableMap(new LinkedHashMap<>(features));
    }

    public static Builder named(String name, int version) {
        return new Builder(name, version);
    }

    public static final class Builder {
        private final String name;
        private final int version;
        private final Map<String, FeatureType> features = new LinkedHashMap<>();

        private Builder(String name, int version) {
            this.name = name;
            this.version = version;
        }

        public Builder code(String key) {
            return feature(key, FeatureType.CODE);
        }

        public Builder number(String key) {
            return feature(key, FeatureType.NUMBER);
        }

        public Builder integer(String key) {
            return feature(key, FeatureType.INTEGER);
        }

        public Builder bool(String key) {
            return feature(key, FeatureType.BOOLEAN);
        }

        private Builder feature(String key, FeatureType type) {
            if (features.put(key, type) != null) {
                throw new IllegalArgumentException("feature " + key + " declared twice in " + name);
            }
            return this;
        }

        public JevFeatureSchema build() {
            return new JevFeatureSchema(name, version, features);
        }
    }
}
