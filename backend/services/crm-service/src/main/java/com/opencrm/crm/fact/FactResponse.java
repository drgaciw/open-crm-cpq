package com.opencrm.crm.fact;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class FactResponse {
    Long id;
    boolean stored;
    boolean appliedToRecord;
    boolean humanProtected;
    String tenantId;
    FactSubjectType subjectType;
    String subjectId;
    String fieldKey;
    String value;
    BigDecimal score;
    FactBand band;
    FactStatus status;
    String method;
    String weightCatalogVersion;
    WriterKind writerKind;
    boolean sensitive;
    boolean contradicting;
    String decidedBy;
    Instant decidedAt;
    Instant observedAt;
    Instant supersededAt;
    List<FactEvidenceResponse> evidence;
}
