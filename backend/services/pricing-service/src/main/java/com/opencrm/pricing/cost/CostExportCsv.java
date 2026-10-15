package com.opencrm.pricing.cost;

import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads the F-2 cost export: RFC 4180 CSV (quoted fields may hold commas, doubled quotes and line breaks), CRLF or LF,
 * an optional UTF-8 BOM, blank lines ignored. The header is required and must name the eight F-2 fields in order.
 *
 * <p>File-level problems (no or wrong header, too many rows) refuse the whole file. Row-level problems reject only that
 * row, with its line number and reason; the other rows are returned for the catalog and idempotency checks.
 */
public final class CostExportCsv {

    /** The F-2 export contract, in order. */
    public static final List<String> HEADER = List.of("productExternalId", "component", "billingUnit", "unitCost",
            "currency", "effectiveFrom", "version", "approvedBy");

    static final int MAX_TEXT = 255;
    static final int MAX_BILLING_UNIT = 100;
    private static final int COST_SCALE = 4;
    private static final Pattern PRODUCT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,254}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private CostExportCsv() {
    }

    /**
     * @param received   data records in the file, rejected ones included (blank lines are not records)
     * @param rows       the well-formed rows, in file order
     * @param rejections the malformed rows, in file order
     */
    public record Result(int received, List<CostExportRow> rows, List<RowRejection> rejections) {
    }

    /** Reads and validates a whole export. */
    public static Result read(String csv, int maxRows) {
        List<Record> records = split(csv == null ? "" : csv);
        if (records.isEmpty() || records.getFirst().malformed() || !header(records.getFirst()).equals(HEADER)) {
            throw new BusinessException("The cost export must start with the F-2 header: " + String.join(",", HEADER),
                    HttpStatus.BAD_REQUEST, "COST_EXPORT_HEADER_INVALID");
        }
        List<Record> data = records.subList(1, records.size());
        if (data.size() > maxRows) {
            throw new BusinessException("A cost export may hold at most " + maxRows + " data rows; this one has "
                    + data.size(), HttpStatus.UNPROCESSABLE_ENTITY, "COST_EXPORT_TOO_LARGE");
        }
        List<CostExportRow> rows = new ArrayList<>();
        List<RowRejection> rejections = new ArrayList<>();
        for (Record record : data) {
            RowRejection rejection = validate(record, rows);
            if (rejection != null) {
                rejections.add(rejection);
            }
        }
        return new Result(data.size(), List.copyOf(rows), List.copyOf(rejections));
    }

    private static List<String> header(Record record) {
        return record.fields().stream().map(String::trim).toList();
    }

    /** Adds the parsed row to {@code rows} and returns null, or returns why the record is rejected. */
    private static RowRejection validate(Record record, List<CostExportRow> rows) {
        List<String> f = record.fields().stream().map(String::trim).toList();
        String id = f.isEmpty() ? null : f.get(0);
        String component = f.size() > 1 ? f.get(1) : null;
        if (record.malformed() || f.size() != HEADER.size()) {
            return reject(record, id, component, RejectionReason.MALFORMED_ROW,
                    record.malformed() ? "unterminated or misplaced quote"
                            : "expected " + HEADER.size() + " fields, found " + f.size());
        }
        if (!PRODUCT_ID.matcher(id).matches()) {
            return reject(record, id, component, RejectionReason.MALFORMED_PRODUCT_ID,
                    "productExternalId must be 1-255 characters of A-Z a-z 0-9 . _ : / -");
        }
        CostComponent parsedComponent;
        try {
            parsedComponent = CostComponent.valueOf(component);
        } catch (IllegalArgumentException e) {
            return reject(record, id, component, RejectionReason.UNKNOWN_COMPONENT,
                    "component must be one of " + List.of(CostComponent.values()));
        }
        String billingUnit = f.get(2);
        if (billingUnit.isEmpty()) {
            return reject(record, id, component, RejectionReason.MISSING_BILLING_UNIT, "billingUnit is required");
        }
        if (billingUnit.length() > MAX_BILLING_UNIT) {
            return reject(record, id, component, RejectionReason.MALFORMED_ROW,
                    "billingUnit is longer than " + MAX_BILLING_UNIT + " characters");
        }
        BigDecimal unitCost;
        try {
            unitCost = new BigDecimal(f.get(3));
        } catch (NumberFormatException e) {
            return reject(record, id, component, RejectionReason.MALFORMED_COST, "unitCost is not a decimal");
        }
        if (unitCost.signum() < 0) {
            return reject(record, id, component, RejectionReason.NEGATIVE_COST, "unitCost must not be negative");
        }
        if (unitCost.stripTrailingZeros().scale() > COST_SCALE || unitCost.precision() - unitCost.scale() > 15) {
            return reject(record, id, component, RejectionReason.MALFORMED_COST,
                    "unitCost must have at most 4 decimal places and 15 integer digits");
        }
        String currency = f.get(4);
        if (!isIsoCurrency(currency)) {
            return reject(record, id, component, RejectionReason.INVALID_CURRENCY,
                    "currency must be an upper-case ISO 4217 code");
        }
        LocalDate effectiveFrom;
        try {
            effectiveFrom = LocalDate.parse(f.get(5));
        } catch (DateTimeParseException e) {
            return reject(record, id, component, RejectionReason.MALFORMED_DATE, "effectiveFrom must be YYYY-MM-DD");
        }
        long version;
        try {
            version = Long.parseLong(f.get(6));
        } catch (NumberFormatException e) {
            version = 0;
        }
        if (version <= 0) {
            return reject(record, id, component, RejectionReason.MALFORMED_VERSION,
                    "version must be a positive whole number");
        }
        String approvedBy = f.get(7);
        if (approvedBy.isEmpty()) {
            return reject(record, id, component, RejectionReason.MISSING_APPROVER, "approvedBy is required");
        }
        if (approvedBy.length() > MAX_TEXT) {
            return reject(record, id, component, RejectionReason.MALFORMED_ROW,
                    "approvedBy is longer than " + MAX_TEXT + " characters");
        }
        rows.add(new CostExportRow(record.line(), id, parsedComponent, billingUnit,
                unitCost.setScale(COST_SCALE, RoundingMode.UNNECESSARY), currency, effectiveFrom, version, approvedBy));
        return null;
    }

    private static boolean isIsoCurrency(String code) {
        if (!CURRENCY.matcher(code).matches()) {
            return false;
        }
        try {
            return Currency.getInstance(code).getCurrencyCode().equals(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static RowRejection reject(Record record, String id, String component, RejectionReason reason,
                                       String detail) {
        return new RowRejection(record.line(), id, component, reason, detail);
    }

    /** One CSV record: the line it starts on, its fields, and whether its quoting was broken. */
    record Record(int line, List<String> fields, boolean malformed) {
    }

    /** RFC 4180 splitting that never throws: broken quoting marks the record malformed instead. */
    static List<Record> split(String csv) {
        String text = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        List<Record> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean quoted = false;
        boolean afterClosingQuote = false;
        boolean malformed = false;
        int line = 1;
        int recordStart = 1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        afterClosingQuote = true;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
                quoted = false;
                afterClosingQuote = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                addRecord(records, recordStart, fields, quoted, malformed);
                fields = new ArrayList<>();
                field.setLength(0);
                quoted = false;
                afterClosingQuote = false;
                malformed = false;
                line++;
                recordStart = line;
            } else if (c == '"') {
                if (field.isEmpty() && !quoted) {
                    inQuotes = true;
                    quoted = true;
                } else {
                    malformed = true;
                    field.append(c);
                }
            } else {
                if (afterClosingQuote) {
                    malformed = true;
                }
                field.append(c);
            }
            i++;
        }
        if (inQuotes) {
            malformed = true;
        }
        if (!field.isEmpty() || !fields.isEmpty() || quoted || malformed) {
            fields.add(field.toString());
            addRecord(records, recordStart, fields, quoted, malformed);
        }
        return records;
    }

    private static void addRecord(List<Record> records, int line, List<String> fields, boolean quoted,
                                  boolean malformed) {
        boolean blankLine = fields.size() == 1 && fields.getFirst().isBlank() && !quoted && !malformed;
        if (!blankLine) {
            records.add(new Record(line, List.copyOf(fields), malformed));
        }
    }
}
