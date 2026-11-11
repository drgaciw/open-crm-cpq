package com.opencrm.crm.deal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a deal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private Long organizationId;

    private Long primaryContactId;

    @PositiveOrZero(message = "Amount must be zero or positive")
    private BigDecimal amount;

    @Size(max = 3, message = "Currency must not exceed 3 characters")
    @Builder.Default
    private String currency = "USD";

    private LocalDate expectedCloseDate;

    @Positive(message = "Term must be a positive number of months")
    private Integer termMonths;

    private String description;

    private String ownerId;

    @Builder.Default
    private DealType type = DealType.NEW_BUSINESS;
}
