package com.opencrm.quote.quote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @NotBlank(message = "Customer external ID is required")
    private String customerExternalId;

    private String dealExternalId;

    private DealType dealType;

    private String subscriptionType;

    @Size(max = 3, message = "Currency code must be 3 characters")
    private String currency;

    @Size(max = 20)
    private String region;

    private Integer durationInMonths;

    private LocalDate startDate;

    private LocalDate endDate;
    private String targetSubscriptionNumber;
    private Long observedRevision;
    private LocalDate subscriptionEffectiveDate;

    private String notes;
}
