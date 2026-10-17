package com.opencrm.common.commercial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Closed CAM-COMMERCIAL-1 projection. Workflow status, row version, approvals, the computed digest
 * and the envelope reference itself are not fields of this value.
 */
public record CommercialEnvelope(
        UUID tenantId,
        UUID documentId,
        int revisionNo,
        String currency,
        LocalDate actionDate,
        LocalDate contractEffectiveDate,
        LocalDate serviceActivationDate,
        LocalDate customerAcceptanceDate,
        String backdatingReasonCode,
        LocalDate coTermDate,
        BigDecimal tcvDelta,
        BigDecimal arrDelta,
        BigDecimal oneTimeTotal,
        BigDecimal creditTotal,
        String sourceInputRef,
        String sourceInputDigest,
        Long observedScheduleRevision,
        List<CommercialLine> lines,
        List<CommercialUnit> units) {

    public CommercialEnvelope {
        lines = List.copyOf(lines);
        units = List.copyOf(units);
        requireText(sourceInputRef);
    }

    public static CommercialEnvelope unpriced(
            UUID tenantId, UUID documentId, int revisionNo, String currency,
            LocalDate actionDate, LocalDate contractEffectiveDate, String sourceInputRef) {
        return new CommercialEnvelope(tenantId, documentId, revisionNo, currency, actionDate, contractEffectiveDate,
                null, null, null, null, null, null, null, null, sourceInputRef, null, null, List.of(), List.of());
    }

    public CommercialEnvelope withTotals(BigDecimal tcv, BigDecimal arr, BigDecimal oneTime, BigDecimal credit) {
        return new CommercialEnvelope(tenantId, documentId, revisionNo, currency, actionDate, contractEffectiveDate,
                serviceActivationDate, customerAcceptanceDate, backdatingReasonCode, coTermDate,
                tcv, arr, oneTime, credit, sourceInputRef, sourceInputDigest, observedScheduleRevision, lines, units);
    }

    public CommercialEnvelope withLine(CommercialLine line) {
        List<CommercialLine> copy = new ArrayList<>(lines);
        copy.add(line);
        return new CommercialEnvelope(tenantId, documentId, revisionNo, currency, actionDate, contractEffectiveDate,
                serviceActivationDate, customerAcceptanceDate, backdatingReasonCode, coTermDate,
                tcvDelta, arrDelta, oneTimeTotal, creditTotal, sourceInputRef, sourceInputDigest,
                observedScheduleRevision, copy, units);
    }

    public CommercialEnvelope withUnit(int unitNo) {
        return withUnit(new CommercialUnit(unitNo, null, null, null, null));
    }

    public CommercialEnvelope withUnit(CommercialUnit unit) {
        List<CommercialUnit> copy = new ArrayList<>(units);
        copy.add(unit);
        return new CommercialEnvelope(tenantId, documentId, revisionNo, currency, actionDate, contractEffectiveDate,
                serviceActivationDate, customerAcceptanceDate, backdatingReasonCode, coTermDate,
                tcvDelta, arrDelta, oneTimeTotal, creditTotal, sourceInputRef, sourceInputDigest,
                observedScheduleRevision, lines, copy);
    }

    JcsCanonicalizer.Value toValue() {
        List<JcsCanonicalizer.Value.Field> fields = new ArrayList<>();
        fields.add(text("actionDate", actionDate == null ? null : actionDate.toString()));
        fields.add(decimal("arrDelta", arrDelta));
        fields.add(text("backdatingReasonCode", backdatingReasonCode));
        fields.add(text("coTermDate", coTermDate == null ? null : coTermDate.toString()));
        fields.add(text("contractEffectiveDate", contractEffectiveDate == null ? null : contractEffectiveDate.toString()));
        fields.add(decimal("creditTotal", creditTotal));
        fields.add(text("currency", currency));
        fields.add(text("customerAcceptanceDate", customerAcceptanceDate == null ? null : customerAcceptanceDate.toString()));
        fields.add(text("documentId", documentId == null ? null : documentId.toString()));
        fields.add(text("formatId", CommercialDigest.FORMAT_ID));
        fields.add(new JcsCanonicalizer.Value.Field("lines", lineValues()));
        fields.add(number("observedScheduleRevision", observedScheduleRevision));
        fields.add(decimal("oneTimeTotal", oneTimeTotal));
        fields.add(new JcsCanonicalizer.Value.Field("revisionNo", new JcsCanonicalizer.Value.IntNum(revisionNo)));
        fields.add(new JcsCanonicalizer.Value.Field("schemaVersion", new JcsCanonicalizer.Value.IntNum(CommercialDigest.SCHEMA_VERSION)));
        fields.add(text("serviceActivationDate", serviceActivationDate == null ? null : serviceActivationDate.toString()));
        fields.add(text("sourceInputDigest", sourceInputDigest));
        fields.add(text("sourceInputRef", sourceInputRef));
        fields.add(decimal("tcvDelta", tcvDelta));
        fields.add(text("tenantId", tenantId == null ? null : tenantId.toString()));
        fields.add(new JcsCanonicalizer.Value.Field("units", unitValues()));
        return new JcsCanonicalizer.Value.Obj(fields);
    }

    private JcsCanonicalizer.Value lineValues() {
        List<CommercialLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(line -> line.changeLineRef().toString()));
        List<JcsCanonicalizer.Value> values = new ArrayList<>();
        for (CommercialLine line : sorted) {
            values.add(new JcsCanonicalizer.Value.Obj(line.fields()));
        }
        return new JcsCanonicalizer.Value.Arr(values);
    }

    private JcsCanonicalizer.Value unitValues() {
        List<CommercialUnit> sorted = new ArrayList<>(units);
        sorted.sort(Comparator.comparingInt(CommercialUnit::unitNo));
        List<JcsCanonicalizer.Value> values = new ArrayList<>();
        for (CommercialUnit unit : sorted) {
            values.add(new JcsCanonicalizer.Value.Obj(unit.fields()));
        }
        return new JcsCanonicalizer.Value.Arr(values);
    }

    private static JcsCanonicalizer.Value.Field text(String name, String value) {
        return new JcsCanonicalizer.Value.Field(name,
                value == null ? new JcsCanonicalizer.Value.Nul() : new JcsCanonicalizer.Value.Str(value));
    }

    private static JcsCanonicalizer.Value.Field decimal(String name, BigDecimal value) {
        return new JcsCanonicalizer.Value.Field(name,
                value == null ? new JcsCanonicalizer.Value.Nul() : new JcsCanonicalizer.Value.Str(DecimalStrings.normalize(value)));
    }

    private static JcsCanonicalizer.Value.Field number(String name, Long value) {
        return new JcsCanonicalizer.Value.Field(name,
                value == null ? new JcsCanonicalizer.Value.Nul() : new JcsCanonicalizer.Value.IntNum(value));
    }

    private static void requireText(String value) {
        if (value == null) {
            throw new CommercialDigestException("source input reference is required");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new CommercialDigestException("invalid Unicode: unpaired surrogate");
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                throw new CommercialDigestException("invalid Unicode: unpaired surrogate");
            }
        }
    }
}
