package com.opencrm.quote.lineitem;

import com.opencrm.quote.ramp.RampScheduleDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator)
@AllArgsConstructor
public class LineItemCreateRequest {

    @NotBlank(message = "Product external ID is required")
    private String productExternalId;

    private String productName;
    private String productType;
    private String lineItemType;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Builder.Default
    private int quantity = 1;

    private String unitOfMeasure;

    @NotNull(message = "List price is required")
    private BigDecimal listPrice;

    private String billingFrequency;
    private int sortOrder;
    private String notes;

    /** Absent means today's flat line. Present steps or an escalator are a ramp schedule. */
    private RampScheduleDto schedule;
    private String sourceSubscriptionLineUuid;
    private com.opencrm.quote.ramp.HybridMigrationContext hybridMigration;
}
