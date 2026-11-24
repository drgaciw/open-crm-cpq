package com.opencrm.quote.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Wire verdict from approval-routing EvaluateQuote. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotePolicyVerdictDto {

    private String quoteId;
    private Long quoteVersion;
    private String verdict;
    private List<Breach> breaches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Breach {
        private Long policyId;
        private Integer policyVersion;
        private String thresholdType;
        private String actual;
        private String limit;
    }
}
