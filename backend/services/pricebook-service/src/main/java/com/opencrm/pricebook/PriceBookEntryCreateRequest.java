package com.opencrm.pricebook;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceBookEntryCreateRequest {

    /** The SKU. */
    @NotBlank(message = "Product external ID is required")
    @Size(max = 255, message = "Product external ID must not exceed 255 characters")
    private String productExternalId;

    @NotNull(message = "List price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "List price must be non-negative")
    private BigDecimal listPrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Floor price must be non-negative")
    private BigDecimal floorPrice;

    @Size(max = 10, message = "Currency must not exceed 10 characters")
    private String currency;

    @Size(max = 20, message = "Unit of measure must not exceed 20 characters")
    private String unitOfMeasure;

    private LocalDate effectiveStartDate;

    private LocalDate effectiveEndDate;
}
