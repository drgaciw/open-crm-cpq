package com.opencrm.pricing.openl;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Scaffolding for OpenL pricing/discount table import and validation.
 */
@Service
public class OpenLTableImportService {

    private static final Pattern OPENL_TABLE_PREFIX = Pattern.compile("^openl-table:.*", Pattern.DOTALL);

    public OpenLTableValidationResult validateImport(OpenLTableImportRequest request) {
        List<String> issues = new ArrayList<>();

        if (request.getTableName() == null || request.getTableName().isBlank()) {
            issues.add("tableName is required");
        }

        String content = request.getContent();
        if (content == null || content.isBlank()) {
            issues.add("content is required");
        } else if (!OPENL_TABLE_PREFIX.matcher(content.trim()).matches()) {
            issues.add("content must start with openl-table: descriptor prefix");
        } else if (!content.contains("discountRate=")) {
            issues.add("content must declare discountRate= for pricing tables");
        }

        if (!issues.isEmpty()) {
            return OpenLTableValidationResult.builder()
                    .valid(false)
                    .status(OpenLTableValidationStatus.REJECTED)
                    .tableName(request.getTableName())
                    .issues(issues)
                    .build();
        }

        return OpenLTableValidationResult.builder()
                .valid(true)
                .status(OpenLTableValidationStatus.READY)
                .tableName(request.getTableName())
                .build();
    }
}
