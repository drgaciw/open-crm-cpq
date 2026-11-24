package com.opencrm.quote.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Quote version sent to approval-routing {@code EvaluateQuote}. Percent fields are percentages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotePolicyEvaluationRequest {

    private String quoteId;
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
