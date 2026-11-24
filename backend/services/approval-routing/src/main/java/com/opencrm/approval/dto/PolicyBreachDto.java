package com.opencrm.approval.dto;

import com.opencrm.approval.domain.ThresholdType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One {@code ExplainPolicyBreach} row: the quote's actual value and the policy limit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyBreachDto {

    private Long policyId;
    private Integer policyVersion;
    private ThresholdType thresholdType;
    private String actual;
    private String limit;
    /** Machine-readable Blocked cause, such as {@code MARGIN_UNKNOWN} or {@code MARGIN_BELOW_FLOOR}. */
    private String reasonCode;
}
