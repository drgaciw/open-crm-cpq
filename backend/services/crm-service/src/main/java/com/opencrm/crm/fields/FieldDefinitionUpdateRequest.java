package com.opencrm.crm.fields;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinitionUpdateRequest {

    private String key;

    @Size(max = 255, message = "Label must not exceed 255 characters")
    private String label;

    private FieldType type;

    private Boolean required;

    private Boolean showOnRecord;

    private Boolean showOnList;

    @Min(value = 0, message = "Position must be zero or greater")
    @Max(value = 100_000, message = "Position is too large")
    private Integer position;

    private Boolean agentFilled;

    @Size(max = 4000, message = "Agent brief must not exceed 4000 characters")
    private String agentBrief;

    @NotNull(message = "Version is required")
    @Min(value = 0, message = "Version must be zero or greater")
    private Long version;
}
