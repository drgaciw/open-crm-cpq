package com.opencrm.quote.ramp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.common.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/** Typed authored credit authority. Pricing evidence or explanatory strings cannot replace it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HybridMigrationContext(String pairId, String role, Integer startMonth, Integer months, BigDecimal bookValue) {
    public HybridMigrationContext {
        if (pairId == null || pairId.isBlank() || !("DECLINING".equals(role) || "RISING".equals(role))
                || startMonth == null || startMonth < 1 || months == null || months < 1 || months > 12
                || ("DECLINING".equals(role) && (bookValue == null || bookValue.signum() < 0))
                || ("RISING".equals(role) && bookValue != null)) {
            throw new BusinessException("Invalid typed hybrid migration context", "HYBRID_MIGRATION_INVALID");
        }
    }
    public QuoteAcceptedEvent.HybridMigrationSnapshot snapshot() {
        return QuoteAcceptedEvent.HybridMigrationSnapshot.builder().pairId(pairId).role(role)
                .startMonth(startMonth).months(months).bookValue(bookValue).build();
    }
    /** Shared annotations are honored by both persisted Jackson 2 and HTTP Jackson 3. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static HybridMigrationContext fromJson(Map<String, Object> fields) {
        Set<String> allowed = Set.of("pairId", "role", "startMonth", "months", "bookValue");
        if (fields == null || !allowed.containsAll(fields.keySet())
                || !(fields.get("pairId") instanceof String pair) || !(fields.get("role") instanceof String role)
                || ("RISING".equals(role) && fields.containsKey("bookValue"))) {
            throw invalidTypes();
        }
        BigDecimal book = null;
        if (fields.containsKey("bookValue")) {
            if (!(fields.get("bookValue") instanceof Number number)) throw invalidTypes();
            try { book = new BigDecimal(number.toString()); }
            catch (NumberFormatException ex) { throw invalidTypes(); }
        }
        return new HybridMigrationContext(pair, role, wholeMonth(fields.get("startMonth")), wholeMonth(fields.get("months")), book);
    }
    private static Integer wholeMonth(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger)) throw invalidTypes();
        try { return new BigInteger(value.toString()).intValueExact(); }
        catch (ArithmeticException ex) { throw invalidTypes(); }
    }
    private static BusinessException invalidTypes() {
        return new BusinessException("Hybrid migration fields require their declared JSON types", "HYBRID_MIGRATION_INVALID");
    }
}
