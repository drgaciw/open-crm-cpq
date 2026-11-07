package com.opencrm.crm.fields;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinitionCreateRequest {

    @NotNull(message = "Entity is required")
    private FieldEntityKind entity;

    @NotBlank(message = "Key is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,62}$", message = "Key must be a lowercase identifier")
    private String key;

    @NotBlank(message = "Label is required")
    @Size(max = 255, message = "Label must not exceed 255 characters")
    private String label;

    @NotNull(message = "Type is required")
    private FieldType type;

    @NotNull(message = "Required flag is required")
    private Boolean required;

    @NotNull(message = "showOnRecord is required")
    private Boolean showOnRecord;

    @NotNull(message = "showOnList is required")
    private Boolean showOnList;

    @NotNull(message = "Position is required")
    @Min(value = 0, message = "Position must be zero or greater")
    @Max(value = 100_000, message = "Position is too large")
    private Integer position;

    @NotNull(message = "agentFilled is required")
    private Boolean agentFilled;

    @Size(max = 4000, message = "Agent brief must not exceed 4000 characters")
    private String agentBrief;

    @Valid
    private List<FieldOptionWriteRequest> options;

    private String createdBy;
    private String updatedBy;
    private String actor;
    private String archivedBy;
}
