package com.opencrm.crm.fields;

import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Server-side type, key, and standard-field rules. No JSON values.
 */
public final class FieldRules {

    public static final int TEXT_MAX = 4000;
    public static final int LONG_TEXT_MAX = 20000;
    public static final int NUMBER_PRECISION = 24;
    public static final int NUMBER_SCALE = 4;

    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9][0-9 .()-]{6,31}$");

    private FieldRules() {
    }

    public static String requireKey(String key) {
        String normalized = key == null ? "" : key.trim();
        if (!KEY.matcher(normalized).matches()) {
            throw bad("Field key must be a lowercase identifier", "FIELD_KEY_INVALID");
        }
        return normalized;
    }

    public static String requireLabel(String label) {
        String normalized = label == null ? "" : label.trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw bad("Field label must be 1 to 255 characters", "FIELD_LABEL_INVALID");
        }
        return normalized;
    }

    public static String normalizeBrief(String brief) {
        if (brief == null || brief.isBlank()) {
            return null;
        }
        String normalized = brief.trim();
        if (normalized.length() > TEXT_MAX) {
            throw bad("Agent brief must be at most " + TEXT_MAX + " characters", "FIELD_BRIEF_INVALID");
        }
        return normalized;
    }

    public static void requirePosition(int position) {
        if (position < 0 || position > 100_000) {
            throw bad("Field position is out of range", "FIELD_POSITION_INVALID");
        }
    }

    public static void rejectStandardKey(FieldEntityKind entity, String key) {
        if (StandardFieldCatalog.isStandard(entity, key)) {
            throw bad("Standard field keys are reserved", "FIELD_KEY_RESERVED");
        }
    }

    public static void rejectKeyChange(String currentKey, String requestedKey) {
        if (requestedKey != null && !currentKey.equals(requestedKey.trim())) {
            throw bad("Field key is immutable", "FIELD_KEY_IMMUTABLE");
        }
    }

    public static void rejectIncompatibleRetype(FieldType current, FieldType requested) {
        if (requested != null && !current.compatibleWith(requested)) {
            throw bad("Field type can change only within " + current.storageClass(), "FIELD_TYPE_INCOMPATIBLE");
        }
    }

    public static void rejectStandardMutation(boolean standard, boolean labelChanged, boolean typeChanged,
                                               boolean requiredChanged, boolean agentChanged, boolean archive) {
        if (!standard) {
            return;
        }
        if (archive || labelChanged || typeChanged || requiredChanged || agentChanged) {
            throw bad("Standard fields can be hidden or reordered only", "FIELD_STANDARD_LOCKED");
        }
    }

    public static void rejectClientActor(String createdBy, String updatedBy, String actor, String archivedBy) {
        if (createdBy != null || updatedBy != null || actor != null || archivedBy != null) {
            throw bad("Actor is taken from the current caller", "CLIENT_ACTOR_FORBIDDEN");
        }
    }

    public static StoredValue requireValue(FieldType type, StoredValue incoming) {
        StoredValue present = incoming == null ? StoredValue.EMPTY : incoming;
        return switch (type) {
            case TEXT -> text(present, TEXT_MAX, false, false, false);
            case LONG_TEXT -> text(present, LONG_TEXT_MAX, false, false, false);
            case URL -> text(present, TEXT_MAX, true, false, false);
            case EMAIL -> text(present, TEXT_MAX, false, true, false);
            case PHONE -> text(present, 40, false, false, true);
            case NUMBER -> number(present);
            case DATE -> date(present);
            case CHECKBOX -> checkbox(present);
            case SELECT -> option(present);
            case USER -> user(present);
        };
    }

    public static void requireActiveOption(boolean optionExists, boolean archived) {
        if (!optionExists || archived) {
            throw bad("Select option is not active on this field", "FIELD_OPTION_INVALID");
        }
    }

    public static void requireSelectOptions(FieldType type, List<String> labels) {
        if (type != FieldType.SELECT) {
            if (labels != null && !labels.isEmpty()) {
                throw bad("Options belong on select fields only", "FIELD_OPTION_INVALID");
            }
            return;
        }
        if (labels == null || labels.isEmpty()) {
            throw bad("A select field needs at least one option", "FIELD_OPTION_REQUIRED");
        }
    }

    private static StoredValue text(StoredValue incoming, int max, boolean url, boolean email, boolean phone) {
        rejectOtherColumns(incoming, true, false, false, false, false, false);
        String value = incoming.text() == null ? "" : incoming.text().trim();
        if (value.isEmpty() || value.length() > max) {
            throw bad("Text value is missing or too long", "FIELD_VALUE_INVALID");
        }
        if (url) {
            requireUrl(value);
        }
        if (email && !EMAIL.matcher(value).matches()) {
            throw bad("Email value is invalid", "FIELD_VALUE_INVALID");
        }
        if (phone && !PHONE.matcher(value).matches()) {
            throw bad("Phone value is invalid", "FIELD_VALUE_INVALID");
        }
        return StoredValue.text(value);
    }

    private static void requireUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equals("http") || scheme.equals("https"))) {
                throw bad("URL value must be an absolute http(s) URL", "FIELD_VALUE_INVALID");
            }
        } catch (IllegalArgumentException ex) {
            throw bad("URL value must be an absolute http(s) URL", "FIELD_VALUE_INVALID");
        }
    }

    private static StoredValue number(StoredValue incoming) {
        rejectOtherColumns(incoming, false, true, false, false, false, false);
        BigDecimal value = incoming.numeric();
        if (value == null) {
            throw bad("Number value is required", "FIELD_VALUE_INVALID");
        }
        if (value.signum() == 0) {
            return StoredValue.numeric(BigDecimal.ZERO.setScale(NUMBER_SCALE));
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int scale = normalized.scale();
        if (scale > NUMBER_SCALE || integerDigits(normalized) > (NUMBER_PRECISION - NUMBER_SCALE)) {
            throw bad("Number value must fit numeric(24,4)", "FIELD_VALUE_INVALID");
        }
        return StoredValue.numeric(normalized.setScale(NUMBER_SCALE));
    }

    /**
     * Digits to the left of the decimal point. {@code precision - scale} is a long subtraction so a
     * negative scale (for example {@code 1E+20}) cannot overflow an int and look smaller than it is.
     * {@code NUMERIC(24,4)} has 20 of those digits.
     */
    private static long integerDigits(BigDecimal value) {
        return (long) value.precision() - (long) value.scale();
    }

    private static StoredValue date(StoredValue incoming) {
        rejectOtherColumns(incoming, false, false, true, false, false, false);
        if (incoming.date() == null) {
            throw bad("Date value is required", "FIELD_VALUE_INVALID");
        }
        return StoredValue.date(incoming.date());
    }

    private static StoredValue checkbox(StoredValue incoming) {
        rejectOtherColumns(incoming, false, false, false, true, false, false);
        if (incoming.bool() == null) {
            throw bad("Checkbox value is required", "FIELD_VALUE_INVALID");
        }
        return StoredValue.bool(incoming.bool());
    }

    private static StoredValue option(StoredValue incoming) {
        rejectOtherColumns(incoming, false, false, false, false, true, false);
        if (incoming.optionId() == null) {
            throw bad("Select option is required", "FIELD_VALUE_INVALID");
        }
        return StoredValue.option(incoming.optionId());
    }

    private static StoredValue user(StoredValue incoming) {
        rejectOtherColumns(incoming, false, false, false, false, false, true);
        String value = incoming.userId() == null ? "" : incoming.userId().trim();
        if (value.isEmpty() || value.length() > 255) {
            throw bad("User value is required", "FIELD_VALUE_INVALID");
        }
        return StoredValue.user(value);
    }

    private static void rejectOtherColumns(StoredValue incoming, boolean text, boolean numeric, boolean date,
                                            boolean bool, boolean option, boolean user) {
        if (!text && incoming.text() != null
                || !numeric && incoming.numeric() != null
                || !date && incoming.date() != null
                || !bool && incoming.bool() != null
                || !option && incoming.optionId() != null
                || !user && incoming.userId() != null) {
            throw bad("Value must use only the column for the field type", "FIELD_VALUE_INVALID");
        }
    }

    public static long requireRecord(FieldEntityKind entity, Long organizationId, Long contactId, Long dealId) {
        int present = count(organizationId) + count(contactId) + count(dealId);
        if (present != 1) {
            throw bad("Exactly one organization, contact, or deal id is required", "FIELD_RECORD_MISMATCH");
        }
        return switch (entity) {
            case ORGANIZATION -> requireMatch(organizationId, "organization");
            case CONTACT -> requireMatch(contactId, "contact");
            case DEAL -> requireMatch(dealId, "deal");
        };
    }

    private static long requireMatch(Long id, String name) {
        if (id == null) {
            throw bad("Value record must be a " + name, "FIELD_RECORD_MISMATCH");
        }
        return id;
    }

    private static int count(Long id) {
        return id == null ? 0 : 1;
    }

    private static BusinessException bad(String message, String code) {
        return new BusinessException(message, HttpStatus.BAD_REQUEST, code);
    }

    /**
     * The six typed columns. At most one is set after validation.
     */
    public record StoredValue(
            String text,
            BigDecimal numeric,
            LocalDate date,
            Boolean bool,
            Long optionId,
            String userId) {

        public static final StoredValue EMPTY = new StoredValue(null, null, null, null, null, null);

        public static StoredValue text(String value) {
            return new StoredValue(value, null, null, null, null, null);
        }

        public static StoredValue numeric(BigDecimal value) {
            return new StoredValue(null, value, null, null, null, null);
        }

        public static StoredValue date(LocalDate value) {
            return new StoredValue(null, null, value, null, null, null);
        }

        public static StoredValue bool(Boolean value) {
            return new StoredValue(null, null, null, value, null, null);
        }

        public static StoredValue option(Long value) {
            return new StoredValue(null, null, null, null, value, null);
        }

        public static StoredValue user(String value) {
            return new StoredValue(null, null, null, null, null, value);
        }
    }
}
