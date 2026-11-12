package com.opencrm.configuration.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Deterministic {@code ConfigurationHash} (DRG-428 / BC-02-05): a lowercase-hex SHA-256 digest of a
 * canonical JSON object built from the configuration's selections (sorted by option key), its catalog
 * version and its rule-set version. Same inputs always produce the same hash — selection insertion order,
 * {@link java.util.Map} implementation, and call count never matter.
 *
 * <p>There is no general-purpose canonical-JSON utility in {@code libs/common} fit for reuse here: the
 * closest existing one ({@code com.opencrm.common.commercial.JcsCanonicalizer}) is package-private, lives
 * in a closed commercial-digest domain, and only canonicalizes integers (unsuitable for arbitrary selection
 * strings). This follows the same idiom (sorted keys, escaped JSON strings, SHA-256 digest) rather than a
 * fragile ad-hoc scheme, scoped to configuration-engine's own hash input shape.
 */
public final class ConfigurationHasher {

    private ConfigurationHasher() {
    }

    /**
     * @param selections     the configuration's selections; must not be {@code null}, may be empty
     * @param catalogVersion the catalog version pinned on the session at StartSession, or {@code null}
     *                       if the caller did not supply one (DRG-424: there is no landed catalog-versioning
     *                       authority to require it)
     * @param ruleSetVersion the compiled rule-set/schema version pinned on the session; must not be
     *                       {@code null}
     * @return the lowercase-hex SHA-256 ConfigurationHash
     */
    public static String compute(Map<String, String> selections, String catalogVersion, String ruleSetVersion) {
        Objects.requireNonNull(selections, "selections");
        Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
        return sha256Hex(canonicalize(selections, catalogVersion, ruleSetVersion));
    }

    /** Package-visible for direct testing of the canonical form, independent of the digest. */
    static String canonicalize(Map<String, String> selections, String catalogVersion, String ruleSetVersion) {
        SortedMap<String, String> sortedSelections = new TreeMap<>(selections);

        StringBuilder out = new StringBuilder();
        out.append('{');
        writeField(out, "catalogVersion", catalogVersion == null ? "" : catalogVersion);
        out.append(',');
        writeField(out, "ruleSetVersion", ruleSetVersion);
        out.append(',');
        writeKey(out, "selections");
        out.append(':').append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : sortedSelections.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeField(out, entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        out.append('}');
        out.append('}');
        return out.toString();
    }

    private static void writeField(StringBuilder out, String key, String value) {
        writeKey(out, key);
        out.append(':');
        writeString(out, value);
    }

    private static void writeKey(StringBuilder out, String key) {
        writeString(out, key);
    }

    /** Minimal JSON string escaping (RFC 8785-style): control characters and the two structural escapes. */
    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c <= 0x1F) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm; this can only indicate a broken runtime.
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
