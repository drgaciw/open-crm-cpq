package com.opencrm.crm.fact;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One evidence observation. Weight is assigned from {@link EvidenceWeights}, never from this body.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = false)
public class EvidenceRequest {

    @NotNull
    private EvidenceKind kind;

    @Size(max = 2000)
    private String url;

    private Instant observedAt;

    @Size(max = 4000)
    private String assertedValue;

    @JsonAnySetter
    public void rejectClientSuppliedProperty(String name, Object ignored) {
        throw new IllegalArgumentException(
                "Do not send " + name + "; the service computes the score and each evidence weight");
    }
}
