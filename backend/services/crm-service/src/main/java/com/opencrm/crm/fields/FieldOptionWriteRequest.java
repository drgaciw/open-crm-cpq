package com.opencrm.crm.fields;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class FieldOptionWriteRequest {

    @NotBlank(message = "Option label is required")
    @Size(max = 255, message = "Option label must not exceed 255 characters")
    private String label;

    @NotNull(message = "Option position is required")
    @Min(value = 0, message = "Option position must be zero or greater")
    @Max(value = 100_000, message = "Option position is too large")
    private Integer position;

    @Min(value = 0, message = "Version must be zero or greater")
    private Long version;
}
