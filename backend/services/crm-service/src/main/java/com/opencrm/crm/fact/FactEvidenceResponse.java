package com.opencrm.crm.fact;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class FactEvidenceResponse {
    EvidenceKind kind;
    BigDecimal weight;
    String url;
    Instant observedAt;
    String assertedValue;
}
