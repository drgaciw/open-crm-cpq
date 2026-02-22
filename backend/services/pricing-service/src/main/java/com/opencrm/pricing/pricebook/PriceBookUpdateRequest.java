package com.opencrm.pricing.pricebook;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for updating a price book.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceBookUpdateRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 64, message = "Region must not exceed 64 characters")
    private String region;

    @Size(max = 10, message = "Currency must not exceed 10 characters")
    private String currency;

    private Boolean isDefault;

    private LocalDate effectiveDate;

    private LocalDate expirationDate;
}
