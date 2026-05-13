package com.opencrm.pricing.openl;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Scaffolds business-user OpenL pricing table import validation.
 */
@Service
@RequiredArgsConstructor
public class OpenLPricingTableImportService {

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "rule_name",
            "product_code",
            "min_quantity",
            "discount_percent",
            "priority"
    );

    private final RuleSetRepository ruleSetRepository;

    @Transactional(readOnly = true)
    public OpenLPricingTableImportResult validate(OpenLPricingTableImportRequest request) {
        ruleSetRepository.findByIdAndDeletedFalse(request.getRuleSetId())
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", request.getRuleSetId()));

        List<OpenLPricingTableValidationIssue> issues = new ArrayList<>();
        List<String[]> rows = parseCsv(request.getCsvContent());
        if (rows.isEmpty()) {
            issues.add(issue(1, null, "EMPTY_TABLE", "Table content must include a header row."));
            return result(request, 0, 0, issues);
        }

        List<String> headers = normalizeHeaders(rows.get(0));
        validateHeaders(headers, issues);
        if (!issues.isEmpty()) {
            return result(request, 0, Math.max(0, rows.size() - 1), issues);
        }

        Map<String, Integer> columnIndex = IntStream.range(0, headers.size())
                .boxed()
                .collect(Collectors.toMap(headers::get, index -> index));
        Set<String> ruleNames = rows.stream()
                .skip(1)
                .map(row -> value(row, columnIndex.get("rule_name")))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());

        int validRows = 0;
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int issueCountBefore = issues.size();
            validateRequired(row, columnIndex, i + 1, issues);
            validateInteger(row, columnIndex, "min_quantity", i + 1, 1, null, issues);
            validateDecimal(row, columnIndex, "discount_percent", i + 1, BigDecimal.ZERO, new BigDecimal("100"), issues);
            validateInteger(row, columnIndex, "priority", i + 1, 100, 1000, issues);

            if (issueCountBefore == issues.size()) {
                validRows++;
            }
        }

        if (ruleNames.size() < rows.size() - 1) {
            issues.add(issue(0, "rule_name", "DUPLICATE_RULE_NAME", "Rule names must be unique within the imported table."));
        }

        return result(request, validRows, Math.max(0, rows.size() - 1), issues);
    }

    private void validateHeaders(List<String> headers, List<OpenLPricingTableValidationIssue> issues) {
        for (String requiredColumn : REQUIRED_COLUMNS) {
            if (!headers.contains(requiredColumn)) {
                issues.add(issue(1, requiredColumn, "MISSING_REQUIRED_COLUMN",
                        "Missing required column: " + requiredColumn));
            }
        }
    }

    private void validateRequired(String[] row, Map<String, Integer> columnIndex, int rowNumber,
            List<OpenLPricingTableValidationIssue> issues) {
        for (String requiredColumn : REQUIRED_COLUMNS) {
            if (value(row, columnIndex.get(requiredColumn)).isBlank()) {
                issues.add(issue(rowNumber, requiredColumn, "REQUIRED_VALUE_MISSING",
                        "Required value is missing."));
            }
        }
    }

    private void validateInteger(String[] row, Map<String, Integer> columnIndex, String column, int rowNumber,
            Integer min, Integer max, List<OpenLPricingTableValidationIssue> issues) {
        String rawValue = value(row, columnIndex.get(column));
        if (rawValue.isBlank()) {
            return;
        }
        try {
            int parsed = Integer.parseInt(rawValue);
            if (min != null && parsed < min || max != null && parsed > max) {
                issues.add(issue(rowNumber, column, "VALUE_OUT_OF_RANGE",
                        rangeMessage(min, max)));
            }
        } catch (NumberFormatException ex) {
            issues.add(issue(rowNumber, column, "INVALID_INTEGER", "Value must be a whole number."));
        }
    }

    private void validateDecimal(String[] row, Map<String, Integer> columnIndex, String column, int rowNumber,
            BigDecimal min, BigDecimal max, List<OpenLPricingTableValidationIssue> issues) {
        String rawValue = value(row, columnIndex.get(column));
        if (rawValue.isBlank()) {
            return;
        }
        try {
            BigDecimal parsed = new BigDecimal(rawValue);
            if (parsed.compareTo(min) < 0 || parsed.compareTo(max) > 0) {
                issues.add(issue(rowNumber, column, "VALUE_OUT_OF_RANGE",
                        "Value must be between " + min + " and " + max + "."));
            }
        } catch (NumberFormatException ex) {
            issues.add(issue(rowNumber, column, "INVALID_DECIMAL", "Value must be a decimal number."));
        }
    }

    private List<String[]> parseCsv(String csvContent) {
        return csvContent.lines()
                .filter(line -> !line.isBlank())
                .map(line -> line.split(",", -1))
                .toList();
    }

    private List<String> normalizeHeaders(String[] headers) {
        return List.of(headers).stream()
                .map(header -> header.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    private String value(String[] row, Integer index) {
        if (index == null || index >= row.length) {
            return "";
        }
        return row[index].trim();
    }

    private OpenLPricingTableValidationIssue issue(int rowNumber, String column, String code, String message) {
        return OpenLPricingTableValidationIssue.builder()
                .rowNumber(rowNumber)
                .column(column)
                .code(code)
                .message(message)
                .build();
    }

    private OpenLPricingTableImportResult result(OpenLPricingTableImportRequest request, int validRows, int totalRows,
            List<OpenLPricingTableValidationIssue> issues) {
        return OpenLPricingTableImportResult.builder()
                .ruleSetId(request.getRuleSetId())
                .tableName(request.getTableName())
                .validRows(validRows)
                .totalRows(totalRows)
                .importable(issues.isEmpty())
                .issues(issues)
                .build();
    }

    private String rangeMessage(Integer min, Integer max) {
        if (max == null) {
            return "Value must be at least " + min + ".";
        }
        return "Value must be between " + min + " and " + max + ".";
    }
}
