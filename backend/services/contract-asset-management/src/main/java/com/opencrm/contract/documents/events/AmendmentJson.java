package com.opencrm.contract.documents.events;

/**
 * Compact JSON with a fixed field order. The hash chain and the audit payload both use it so the
 * bytes do not depend on a mapper configuration.
 */
public final class AmendmentJson {

    private final StringBuilder body = new StringBuilder(256);
    private boolean first = true;

    public AmendmentJson() {
        body.append('{');
    }

    public AmendmentJson number(String key, long value) {
        field(key);
        body.append(value);
        return this;
    }

    public AmendmentJson nullableInt(String key, Integer value) {
        if (value == null) {
            return text(key, null);
        }
        return number(key, value.longValue());
    }

    public AmendmentJson text(String key, String value) {
        field(key);
        if (value == null) {
            body.append("null");
            return this;
        }
        body.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> body.append("\\\"");
                case '\\' -> body.append("\\\\");
                case '\b' -> body.append("\\b");
                case '\f' -> body.append("\\f");
                case '\n' -> body.append("\\n");
                case '\r' -> body.append("\\r");
                case '\t' -> body.append("\\t");
                default -> {
                    if (current < 0x20) {
                        body.append(String.format("\\u%04x", (int) current));
                    } else {
                        body.append(current);
                    }
                }
            }
        }
        body.append('"');
        return this;
    }

    public String finish() {
        body.append('}');
        return body.toString();
    }

    private void field(String key) {
        if (!first) {
            body.append(',');
        }
        first = false;
        body.append('"').append(key).append("\":");
    }
}
