package com.opencrm.common.event.dto.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Versioned published-language contract for {@code cpq.discount.approval.required}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountApprovalRequiredEventV1 {

    public static final int SCHEMA_VERSION = 1;

    @Builder.Default
    private int schemaVersion = SCHEMA_VERSION;

    private String quoteId;
    private String lineId;
    private BigDecimal marginDelta;
    private BigDecimal requestedDiscount;
    private String currencyCode;
}
