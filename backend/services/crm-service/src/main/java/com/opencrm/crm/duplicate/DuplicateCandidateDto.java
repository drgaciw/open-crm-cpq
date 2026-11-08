package com.opencrm.crm.duplicate;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One read-only duplicate suggestion. {@code candidateId} is the CRM row id used by the source endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"candidateId", "candidateType", "reason", "matchedValue"})
public class DuplicateCandidateDto {

    private Long candidateId;
    private DuplicateCandidateType candidateType;
    private DuplicateMatchReason reason;
    private String matchedValue;
}
