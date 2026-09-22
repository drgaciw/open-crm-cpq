package com.opencrm.jev.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencrm.jev.state.JevFeatureSchema.FeatureType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The only way domain code can assemble what is sent to Jev. Values are checked against the use case
 * schema as they are added, so a name, an email or a free-text note cannot be attached by accident.
 */
public final class JevState {

    /** Codes are short and carry no whitespace, which rules out sentences and most personal data. */
    static final int MAX_CODE_LENGTH = 64;
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_.:+-]{1," + MAX_CODE_LENGTH + "}");

    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final JevFeatureSchema schema;
    private final Map<String, Object> values;

    private JevState(JevFeatureSchema schema, Map<String, Object> values) {
        this.schema = schema;
        this.values = Collections.unmodifiableMap(values);
    }

    public static Builder builder(JevFeatureSchema schema) {
        return new Builder(schema);
    }

    public JevFeatureSchema schema() {
        return schema;
    }

    /** Key-sorted view of the features. */
    public Map<String, Object> values() {
        return values;
    }

    /** Deterministic JSON: same features always give the same text, and so the same hash. */
    public String canonicalJson() {
        try {
            return CANONICAL.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise Jev state", e);
        }
    }

    public String sha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String keyed = schema.name() + "@" + schema.version() + ":" + canonicalJson();
            return HexFormat.of().formatHex(digest.digest(keyed.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Builder {
        private final JevFeatureSchema schema;
        private final Map<String, Object> values = new TreeMap<>();

        private Builder(JevFeatureSchema schema) {
            this.schema = schema;
        }

        /** @param value null is kept as an explicit unknown */
        public Builder put(String key, Object value) {
            FeatureType type = schema.features().get(key);
            if (type == null) {
                throw new FeatureNotAllowedException(
                        "Feature " + key + " is not in schema " + schema.name() + " v" + schema.version());
            }
            values.put(key, value == null ? null : checked(key, type, value));
            return this;
        }

        private Object checked(String key, FeatureType type, Object value) {
            switch (type) {
                case CODE -> {
                    String code = value instanceof Enum<?> e ? e.name() : value.toString();
                    if (!CODE.matcher(code).matches()) {
                        throw new FeatureNotAllowedException(
                                "Feature " + key + " must be a short code without whitespace");
                    }
                    return code;
                }
                case NUMBER -> {
                    if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                        throw new FeatureNotAllowedException("Feature " + key + " must be a finite number");
                    }
                    return n.doubleValue();
                }
                case INTEGER -> {
                    if (!(value instanceof Integer || value instanceof Long || value instanceof Short)) {
                        throw new FeatureNotAllowedException("Feature " + key + " must be an integer");
                    }
                    return ((Number) value).longValue();
                }
                case BOOLEAN -> {
                    if (!(value instanceof Boolean)) {
                        throw new FeatureNotAllowedException("Feature " + key + " must be a boolean");
                    }
                    return value;
                }
                default -> throw new IllegalStateException("Unhandled feature type " + type);
            }
        }

        public JevState build() {
            return new JevState(schema, new TreeMap<>(values));
        }
    }
}
