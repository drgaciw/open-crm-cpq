package com.opencrm.quote.lifecycle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLineResponse {

    private UUID lineId;
    private Integer lineNo;
    private String actionCode;
    private String quantitySemantics;
    private Long observedCommercialRevision;
    private String startQty;
    private String deltaQty;
    private String endQty;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String targetSubscriptionId;
    private String amendMode;
    private String prorationMethod;
    private String policyDecisionId;
    private String policyId;
    private String policyVersion;
    private String pricingTraceId;
    private String productExternalId;
    private String pricebookVersion;
    private String configurationHash;
}
