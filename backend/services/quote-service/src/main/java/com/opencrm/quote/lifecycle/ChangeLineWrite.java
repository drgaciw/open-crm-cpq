package com.opencrm.quote.lifecycle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLineWrite {

    @NotBlank
    private String actionCode;

    @NotBlank
    private String quantitySemantics;

    private String targetSubscriptionId;

    private String sourceAcceptedLineUuid;

    private Long observedCommercialRevision;

    @NotBlank
    private String startQty;

    @NotBlank
    private String deltaQty;

    @NotBlank
    private String endQty;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String productExternalId;
}
