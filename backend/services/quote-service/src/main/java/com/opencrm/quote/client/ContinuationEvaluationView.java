package com.opencrm.quote.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Quote-local view of {@code POST /api/v1/pricing/continuation/evaluate}.
 * Policy decision identity is not on the execute/simulate view. DRG-609 option B.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContinuationEvaluationView {

    private String traceId;
    private String policyId;
    private String policyVersion;
    private List<Line> lines;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Line {
        private String lineRef;
        private String policyDecisionId;
        private String policyId;
        private String policyVersion;
        private String traceId;
        private String productExternalId;
        private String pricebookVersion;
        /** Null until DRG-428 replaces the interim pin. Phase A stores that null. */
        private String configurationHash;
        private String procedureStatus;
        private String unitRate;
        private String amount;
        private String endQuantity;
    }
}
