package com.opencrm.crm.fact;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * ProposeFact body. There is no score and no weight. Unknown properties are rejected
 * so a model cannot smuggle either number in.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = false)
public class ProposeFactRequest {

    @NotBlank
    @Size(max = 128)
    @Pattern(
            regexp = "[A-Za-z][A-Za-z0-9_]{0,127}",
            message = "fieldKey must start with a letter and use letters, digits, or underscores")
    private String fieldKey;

    @NotBlank
    @Size(max = 4000)
    private String value;

    @NotBlank
    @Size(max = 64)
    @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,63}",
            message = "method must be a short token")
    private String method;

    @NotEmpty
    @Size(max = 20)
    private List<@Valid EvidenceRequest> evidence;

    /**
     * Jackson 3 ignores unknown properties unless a handler rejects them.
     * Score is not a field. This is what stops a model from sending one.
     */
    @JsonAnySetter
    public void rejectClientSuppliedProperty(String name, Object ignored) {
        throw new IllegalArgumentException(
                "Do not send " + name + "; the service computes the score and each evidence weight");
    }
}
