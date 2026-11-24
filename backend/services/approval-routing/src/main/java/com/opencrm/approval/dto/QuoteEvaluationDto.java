package com.opencrm.approval.dto;

import com.opencrm.approval.domain.PolicyVerdict;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code GetVerdict} for a quote version, with each breach's actual value and limit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteEvaluationDto {

    private String quoteId;
    private Long quoteVersion;
    private PolicyVerdict verdict;
    private List<PolicyBreachDto> breaches;
}
