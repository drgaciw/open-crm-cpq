package com.opencrm.crm.fields;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Rows read and written by the registry store. {@code standard} is not a column;
 * the service derives it from {@link StandardFieldCatalog}.
 */
public final class FieldRows {

    private FieldRows() {
    }

    public record Definition(
            Long id,
            String tenant,
            FieldEntityKind entity,
            String key,
            String label,
            FieldType type,
            boolean required,
            boolean showOnRecord,
            boolean showOnList,
            int position,
            boolean agentFilled,
            String agentBrief,
            Instant archivedAt,
            String createdBy,
            String updatedBy,
            long version) {
    }

    public record Option(
            Long id,
            long definitionId,
            String label,
            int position,
            Instant archivedAt,
            String createdBy,
            String updatedBy,
            long version) {
    }

    public record Value(
            Long id,
            long definitionId,
            Long organizationId,
            Long contactId,
            Long dealId,
            String text,
            BigDecimal numeric,
            LocalDate date,
            Boolean bool,
            Long optionId,
            String userId,
            String createdBy,
            String updatedBy,
            long version) {
    }
}
