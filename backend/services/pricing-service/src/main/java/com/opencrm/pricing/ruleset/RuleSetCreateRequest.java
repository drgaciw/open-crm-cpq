package com.opencrm.pricing.ruleset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for creating a rule set.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSetCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @Builder.Default
    private String engineType = "DROOLS";

    private LocalDate effectiveDate;

    private LocalDate expirationDate;
}
