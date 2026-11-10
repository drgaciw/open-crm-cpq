package com.opencrm.crm.deal;

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
 * Request DTO for updating a deal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealUpdateRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private Long organizationId;

    private Long primaryContactId;

    private DealStage stage;

    @PositiveOrZero(message = "Amount must be zero or positive")
    private BigDecimal amount;

    @Size(max = 3, message = "Currency must not exceed 3 characters")
    private String currency;

    private LocalDate expectedCloseDate;

    @Positive(message = "Term must be a positive number of months")
    private Integer termMonths;

    private String description;

    private String ownerId;

    private DealType type;
}
