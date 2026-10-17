package com.opencrm.common.commercial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** RFC 8785 canonical JSON for the closed commercial projection. Numbers here are integers only. */
final class JcsCanonicalizer {

    private JcsCanonicalizer() {
    }

    static String canonicalize(Value value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Value value) {
        switch (value) {
            case Value.Nul ignored -> out.append("null");
            case Value.Bool bool -> out.append(bool.value() ? "true" : "false");
            case Value.IntNum number -> {
                if (number.value() > 9_007_199_254_740_991L || number.value() < -9_007_199_254_740_991L) {
                    throw new CommercialDigestException("unsupported numeric input");
                }
                out.append(number.value());
            }
            case Value.Str str -> writeString(out, str.value());
            case Value.Arr arr -> {
                out.append('[');
                for (int i = 0; i < arr.values().size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    write(out, arr.values().get(i));
                }
                out.append(']');
            }
            case Value.Obj obj -> {
                List<Value.Field> fields = new ArrayList<>(obj.fields());
                fields.sort(Comparator.comparing(Value.Field::name));
                String previous = null;
                out.append('{');
                for (int i = 0; i < fields.size(); i++) {
                    Value.Field field = fields.get(i);
                    if (field.name().equals(previous)) {
                        throw new CommercialDigestException("duplicate JSON key: " + field.name());
                    }
                    previous = field.name();
                    if (i > 0) {
                        out.append(',');
                    }
                    writeString(out, field.name());
                    out.append(':');
                    write(out, field.value());
                }
                out.append('}');
            }
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c <= 0x1F) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c)) {
                        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                            throw new CommercialDigestException("invalid Unicode: unpaired surrogate");
                        }
                        out.append(c).append(value.charAt(++i));
                    } else if (Character.isLowSurrogate(c)) {
                        throw new CommercialDigestException("invalid Unicode: unpaired surrogate");
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    sealed interface Value {
        record Nul() implements Value {
        }

        record Bool(boolean value) implements Value {
        }

        record IntNum(long value) implements Value {
        }

        record Str(String value) implements Value {
        }

        record Arr(List<Value> values) implements Value {
        }

        record Field(String name, Value value) {
        }

        record Obj(List<Field> fields) implements Value {
        }
    }
}
