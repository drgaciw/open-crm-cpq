package com.opencrm.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Quote version submitted to {@code EvaluateQuote}. Percent fields are percentages. Null measurements are
 * unmeasured. The caller tenant is the token, not this body: {@code tenantId} is the quote's own tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteEvaluationRequest {

    @NotBlank
    private String quoteId;

    @NotNull
    private Long quoteVersion;

    private String tenantId;
    private String segment;
    private String productFamily;
    private String region;
    private BigDecimal discountPercent;
    private BigDecimal marginPercent;
    private Integer termMonths;
    private List<String> clauseCodes;
    private LocalDate asOf;
}
